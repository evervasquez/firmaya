package pe.firmador;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.validation.PAdESSignature;
import eu.europa.esig.dss.pades.validation.PDFDocumentAnalyzer;
import eu.europa.esig.dss.pades.validation.PdfSignatureDictionary;
import eu.europa.esig.dss.spi.DSSASN1Utils;
import eu.europa.esig.dss.spi.signature.AdvancedSignature;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;

/**
 * Lee las firmas ya incrustadas en un PDF y describe lo que contienen.
 *
 * <p>Comprueba la integridad criptografica de cada firma (que el documento no se haya
 * alterado despues de firmar y que la firma corresponda al certificado incluido), pero
 * <strong>no</strong> valida la cadena de confianza contra las CA de RENIEC ni consulta
 * CRL u OCSP: eso lo hace el validador oficial.</p>
 *
 * <p>Esta clase no guarda estado y es segura para varios hilos.</p>
 */
public final class VerificadorPdf {

    /**
     * Describe todas las firmas del documento, de la mas antigua a la mas reciente.
     *
     * @param pdf documento a inspeccionar
     * @return lista inmutable; vacia si el documento no tiene firmas
     * @throws FirmaException si el archivo no existe o no se puede analizar
     */
    public List<DatosFirma> verificar(Path pdf) {
        Objects.requireNonNull(pdf, "pdf es obligatorio");
        if (!Files.isRegularFile(pdf)) {
            throw new FirmaException("No existe el PDF: " + pdf);
        }

        PDFDocumentAnalyzer analizador = new PDFDocumentAnalyzer(new FileDocument(pdf.toFile()));
        analizador.setCertificateVerifier(new CommonCertificateVerifier());

        List<DatosFirma> encontradas = new ArrayList<>();
        for (AdvancedSignature firma : analizador.getSignatures()) {
            encontradas.add(describir(firma, pdf));
        }
        return List.copyOf(encontradas);
    }

    private DatosFirma describir(AdvancedSignature firma, Path pdf) {
        firma.checkSignatureIntegrity();
        boolean integra = firma.getSignatureCryptographicVerification().isSignatureValid();

        String filtro = DatosFirma.DESCONOCIDO;
        String subFiltro = DatosFirma.DESCONOCIDO;
        String motivo = DatosFirma.DESCONOCIDO;
        if (firma instanceof PAdESSignature pades) {
            PdfSignatureDictionary diccionario = pades.getPdfSignatureDictionary();
            filtro = textoODesconocido(diccionario.getFilter());
            subFiltro = textoODesconocido(diccionario.getSubFilter());
            motivo = textoODesconocido(diccionario.getReason());
        }

        return new DatosFirma(
                titularDe(firma),
                motivo,
                fechaDe(firma, pdf),
                filtro,
                subFiltro,
                nombreDelNivel(firma),
                nombreDelAlgoritmo(firma),
                integra);
    }

    private Instant fechaDe(AdvancedSignature firma, Path pdf) {
        // DSS expone la fecha como java.util.Date; se convierte aqui mismo a java.time.
        Date fecha = firma.getSigningTime();
        if (fecha == null) {
            throw new FirmaException("La firma de " + pdf + " no declara fecha de firma");
        }
        return fecha.toInstant();
    }

    private String titularDe(AdvancedSignature firma) {
        CertificateToken certificado = firma.getSigningCertificateToken();
        if (certificado == null) {
            return DatosFirma.DESCONOCIDO;
        }
        return textoODesconocido(DSSASN1Utils.getSubjectCommonName(certificado));
    }

    private String nombreDelNivel(AdvancedSignature firma) {
        if (firma.getDataFoundUpToLevel() == null) {
            return DatosFirma.DESCONOCIDO;
        }
        return firma.getDataFoundUpToLevel().name().replace('_', '-');
    }

    private String nombreDelAlgoritmo(AdvancedSignature firma) {
        if (firma.getSignatureAlgorithm() == null) {
            return DatosFirma.DESCONOCIDO;
        }
        return firma.getSignatureAlgorithm().getJCEId();
    }

    private String textoODesconocido(String valor) {
        return valor == null || valor.isBlank() ? DatosFirma.DESCONOCIDO : valor;
    }
}
