package lars.ba.cda2r4;

import org.eclipse.emf.ecore.EPackage;
import org.openhealthtools.mdht.uml.cda.CDAPackage;
import org.openhealthtools.mdht.uml.cda.consol.ConsolPackage;
import org.openhealthtools.mdht.uml.cda.util.CDAUtil;

import java.util.concurrent.Callable;

public class CdaIsolationUtil {
    // Statische Methode zum isolierten Ausführen von CDA-Code
    public static <T> T executeInIsolation(Callable<T> action) throws Exception {
        ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
        ClassLoader cdaClassLoader = CDAUtil.class.getClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(cdaClassLoader);

            // EMF-Pakete explizit registrieren
            EPackage.Registry.INSTANCE.put(CDAPackage.eNS_URI, CDAPackage.eINSTANCE);
            EPackage.Registry.INSTANCE.put(ConsolPackage.eNS_URI, ConsolPackage.eINSTANCE);

            return action.call();
        } finally {
            Thread.currentThread().setContextClassLoader(originalCL);
        }
    }
}