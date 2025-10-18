package org.nyet.util;

import java.lang.reflect.Method;

import org.nyet.ecuxplot.MessageDialog;

public class BrowserLaunch {

    private static final String errMsg =
	"Error attempting to launch web browser";

    public static void openURL(String url) {
	try {
	    final Class<?> Desktop = Class.forName("java.awt.Desktop");
	    final Method isDesktopSupported =
		Desktop.getDeclaredMethod("isDesktopSupported");
	    if ((Boolean) isDesktopSupported.invoke(null)) {
		final Method getDesktop =
		    Desktop.getDeclaredMethod("getDesktop");
		final Method browse =
		    Desktop.getDeclaredMethod("browse", java.net.URI.class);
		final Object desktop = getDesktop.invoke(null);
		browse.invoke(desktop, (new java.net.URI(url)));
		return;
	    }
	} catch (final Exception e) {
	}
	fallback(url);
    }

    private static void fallback(String url) {
        final String osName = System.getProperty("os.name");
        try {
	    if (osName.startsWith("Mac OS")) {
		final Class<?> fileMgr =
		    Class.forName("com.apple.eio.FileManager");
		final Method openURL = fileMgr.getDeclaredMethod("openURL",
		    new Class[] {String.class});
		openURL.invoke(null, new Object[] {url});
	    } else if (osName.startsWith("Windows"))
		new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
	    else { //assume Unix or Linux
		final String[] browsers = {
		    "firefox", "iceweasel", "opera", "konqueror", "epiphany",
		    "mozilla", "netscape" };
		String browser = null;
		for (int i = 0; i <browsers.length && browser == null; i++)
		    if (new ProcessBuilder("which", browsers[i]).start().waitFor() == 0)
		    browser = browsers[i];
		if (browser == null)
		    throw new Exception("Could not find web browser");
		else
		    new ProcessBuilder(browser, url).start();
	    }
        } catch (final Exception e) {
	    MessageDialog.showMessageDialog(null,
		errMsg + ":\n" + e.getLocalizedMessage());
	}
    }
}
