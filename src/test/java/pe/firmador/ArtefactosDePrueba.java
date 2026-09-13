package pe.firmador;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Genera un PDF y un certificado de juego para las pruebas, de modo que se pueda ejercitar
 * la firma completa sin tocar el certificado real del usuario.
 */
public final class ArtefactosDePrueba {

    private static final int BITS_RSA = 2048;
    private static final int DIAS_DE_VIGENCIA = 365;

    private ArtefactosDePrueba() {
    }

    public static Path crearPdf(Path destino, int paginas) throws Exception {
        try (PDDocument documento = new PDDocument()) {
            for (int i = 1; i <= paginas; i++) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                escribirTitulo(documento, pagina, "Documento de prueba, página " + i);
            }
            documento.save(destino.toFile());
        }
        return destino;
    }

    private static void escribirTitulo(PDDocument documento, PDPage pagina, String texto) throws Exception {
        try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
            contenido.beginText();
            contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contenido.newLineAtOffset(72, 750);
            contenido.showText(texto);
            contenido.endText();
        }
    }

    /**
     * @param momentoDeFirma instante en el que se usara el certificado; el periodo de validez
     *                       se construye alrededor de el porque DSS se niega a firmar con un
     *                       certificado que aun no es valido o que ya vencio.
     */
    public static Path crearCertificado(Path destino, String nombreComun, char[] contrasena,
                                        Instant momentoDeFirma) throws Exception {
        return crearCertificado(destino, nombreComun, "", contrasena, momentoDeFirma, DIAS_DE_VIGENCIA);
    }

    /**
     * @param documento      valor del {@code serialNumber} del sujeto, como {@code DNI-12345678};
     *                       cadena vacia para no ponerlo
     * @param diasDeVigencia cuanto dura el certificado desde el dia anterior al momento de firma
     */
    public static Path crearCertificado(Path destino, String nombreComun, String documento,
                                        char[] contrasena, Instant momentoDeFirma,
                                        int diasDeVigencia) throws Exception {
        return crearCertificado(destino, nombreComun, documento, contrasena, momentoDeFirma,
                diasDeVigencia, Uso.FIRMA_DE_DOCUMENTOS);
    }

    /** Para que sirve el certificado de prueba, que es lo que decide si la aplicacion lo ofrece. */
    public enum Uso {

        /** Certificado de persona: firma documentos. */
        FIRMA_DE_DOCUMENTOS,

        /** Certificado de firmar aplicaciones, como los de Apple Developer. */
        FIRMA_DE_CODIGO,

        /** Sin extensiones de uso: X.509 no restringe para que vale. */
        SIN_DECLARAR
    }

    /**
     * @param uso extensiones de uso que se escriben en el certificado
     */
    public static Path crearCertificado(Path destino, String nombreComun, String documento,
                                        char[] contrasena, Instant momentoDeFirma,
                                        int diasDeVigencia, Uso uso) throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(BITS_RSA, SecureRandom.getInstanceStrong());
        KeyPair par = generador.generateKeyPair();

        X509Certificate certificado = autofirmar(par, nombreComun, documento, momentoDeFirma,
                diasDeVigencia, uso);

        KeyStore almacen = KeyStore.getInstance("PKCS12");
        almacen.load(null, null);
        almacen.setKeyEntry("firmante", par.getPrivate(), contrasena, new Certificate[]{certificado});
        try (OutputStream salida = Files.newOutputStream(destino)) {
            almacen.store(salida, contrasena);
        }
        return destino;
    }

    private static void agregarUso(JcaX509v3CertificateBuilder constructor, Uso uso)
            throws Exception {
        if (uso == Uso.SIN_DECLARAR) {
            return;
        }
        if (uso == Uso.FIRMA_DE_CODIGO) {
            constructor.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature));
            constructor.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_codeSigning));
            return;
        }
        constructor.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        constructor.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(new KeyPurposeId[]{
                        KeyPurposeId.id_kp_clientAuth, KeyPurposeId.id_kp_emailProtection}));
    }

    private static X509Certificate autofirmar(KeyPair par, String nombreComun, String documento,
                                              Instant momentoDeFirma, int diasDeVigencia, Uso uso)
            throws Exception {
        String conDocumento = documento.isBlank() ? "" : ",SERIALNUMBER=" + documento;
        X500Name sujeto = new X500Name("CN=" + nombreComun + conDocumento + ",O=PRUEBA,C=PE");
        Instant desde = momentoDeFirma.minus(1, ChronoUnit.DAYS);
        Instant hasta = desde.plus(diasDeVigencia, ChronoUnit.DAYS);

        JcaX509v3CertificateBuilder constructor = new JcaX509v3CertificateBuilder(
                sujeto,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(desde),
                Date.from(hasta),
                sujeto,
                par.getPublic());

        agregarUso(constructor, uso);

        ContentSigner firmante = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(new BouncyCastleProvider())
                .build(par.getPrivate());

        return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(constructor.build(firmante));
    }
}
