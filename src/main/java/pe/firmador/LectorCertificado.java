package pe.firmador;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;

/**
 * Traduce un certificado X.509 a los pocos datos que la aplicacion muestra: titular, documento,
 * emisor y vigencia.
 *
 * <p>Se apoya en Bouncy Castle, que ya viene con el motor de firma, para leer el nombre
 * distinguido sin tener que interpretar cadenas a mano.</p>
 *
 * <p>No guarda estado: es segura para varios hilos.</p>
 */
public final class LectorCertificado {

    private static final Logger log = LoggerFactory.getLogger(LectorCertificado.class);

    /**
     * Uso extendido «firma de codigo»: identifica los certificados de firmar aplicaciones, como
     * los de Apple Developer. No sirven para firmar documentos.
     */
    private static final String USO_FIRMA_DE_CODIGO = "1.3.6.1.5.5.7.3.3";

    /** Posicion de {@code digitalSignature} dentro de la extension keyUsage. */
    private static final int FIRMA_DIGITAL = 0;

    /** Posicion de {@code nonRepudiation} (no repudio) dentro de la extension keyUsage. */
    private static final int NO_REPUDIO = 1;

    private LectorCertificado() {
    }

    /**
     * Si un certificado sirve para firmar <em>documentos</em>.
     *
     * <p>El criterio es la aptitud declarada por el propio certificado, no donde este guardado:</p>
     * <ul>
     *   <li>su {@code keyUsage} debe permitir {@code digitalSignature} o {@code nonRepudiation};
     *       si no trae la extension, X.509 no restringe el uso y se acepta;</li>
     *   <li>se rechaza si su uso extendido incluye <em>firma de codigo</em>: esos certificados
     *       —los de Apple Developer, por ejemplo— firman aplicaciones, y una firma hecha con
     *       ellos no tiene valor como firma de un documento.</li>
     * </ul>
     *
     * <p>La vigencia no se juzga aqui: la decide {@link DatosCertificado#vigencia} con el reloj
     * que corresponda, para que la interfaz pueda explicarla en vez de ocultar el certificado.</p>
     *
     * @param certificado certificado a evaluar
     * @return {@code true} si con el se puede firmar un documento
     */
    public static boolean sirveParaFirmarDocumentos(X509Certificate certificado) {
        Objects.requireNonNull(certificado, "certificado es obligatorio");
        return permiteFirmar(certificado) && !esDeFirmaDeCodigo(certificado);
    }

    private static boolean permiteFirmar(X509Certificate certificado) {
        boolean[] usos = certificado.getKeyUsage();
        if (usos == null) {
            return true;
        }
        return (usos.length > FIRMA_DIGITAL && usos[FIRMA_DIGITAL])
                || (usos.length > NO_REPUDIO && usos[NO_REPUDIO]);
    }

    private static boolean esDeFirmaDeCodigo(X509Certificate certificado) {
        try {
            List<String> usosExtendidos = certificado.getExtendedKeyUsage();
            return usosExtendidos != null && usosExtendidos.contains(USO_FIRMA_DE_CODIGO);
        } catch (CertificateParsingException e) {
            // Un uso extendido ilegible no permite afirmar que el certificado sea de firma de
            // codigo; se deja pasar y sera la comprobacion de la firma la que falle si no sirve.
            log.debug("No se pudo leer el uso extendido del certificado", e);
            return false;
        }
    }

    /**
     * @param certificado certificado ya leido de un .p12 o del llavero
     * @return sus datos legibles
     * @throws FirmaException si el certificado no tiene nombre comun (CN), porque entonces no
     *                        hay nombre que imprimir en el sello
     */
    public static DatosCertificado datosDe(X509Certificate certificado) {
        Objects.requireNonNull(certificado, "certificado es obligatorio");

        X500Name sujeto = X500Name.getInstance(certificado.getSubjectX500Principal().getEncoded());
        X500Name emisor = X500Name.getInstance(certificado.getIssuerX500Principal().getEncoded());

        String titular = valorDe(sujeto, BCStyle.CN);
        if (titular.isBlank()) {
            throw new FirmaException("El certificado del firmante no tiene nombre común (CN)");
        }
        return new DatosCertificado(
                titular,
                valorDe(sujeto, BCStyle.SERIALNUMBER),
                valorDe(emisor, BCStyle.CN),
                certificado.getNotBefore().toInstant(),
                certificado.getNotAfter().toInstant());
    }

    /** Primer valor de un atributo del nombre distinguido, o cadena vacia si no esta. */
    private static String valorDe(X500Name nombre, ASN1ObjectIdentifier atributo) {
        RDN[] partes = nombre.getRDNs(atributo);
        if (partes.length == 0 || partes[0].getFirst() == null) {
            return "";
        }
        return IETFUtils.valueToString(partes[0].getFirst().getValue()).trim();
    }
}
