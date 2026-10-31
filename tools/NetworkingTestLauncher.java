import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/** Runs the real JUnit engine without a Maven download; used by test-network.ps1. */
public final class NetworkingTestLauncher {
    public static void main(String[] args) {
        var request = LauncherDiscoveryRequestBuilder.request().selectors(
                DiscoverySelectors.selectPackage("com.overleaf.common.sync"),
                DiscoverySelectors.selectPackage("com.overleaf.server"),
                DiscoverySelectors.selectPackage("com.overleaf.client.net")).build();
        var summary = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(summary);
        launcher.execute(request);
        var output = new java.io.PrintWriter(System.out);
        summary.getSummary().printTo(output);
        summary.getSummary().printFailuresTo(output);
        if (summary.getSummary().getTestsFoundCount() == 0 || summary.getSummary().getTotalFailureCount() != 0) System.exit(1);
    }
}
