/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core.label;

import org.zkoss.util.resource.LabelLocator;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * @author jgomer
 */
public class PluginLabelLocator implements LabelLocator, Closeable {

    private static final String DEFAULT_PROPS_FILE = "zk-label";

    private JarFile jarFile;
    private String uri;
    private String basePath;
    private String subDirectory;

    public PluginLabelLocator(Path path, String subDir) {

        try {
            this.subDirectory = subDir;
            this.basePath = path.toString();
            uri = path.toUri().toString();

            if (Files.isRegularFile(path) && basePath.toLowerCase().endsWith(".jar")) {
                jarFile = new JarFile(path.toFile(), false, ZipFile.OPEN_READ);
            }
        } catch (IOException e) {
            //Intentionally left empty
        }
    }

    public URL locate(Locale locale) throws MalformedURLException {

        String location = null;

        String suffix = DEFAULT_PROPS_FILE;
        suffix += locale == null ? "" : locale.toString();
        suffix += ".properties";

        if (jarFile == null) {
            Path path = Paths.get(basePath, subDirectory, suffix);
            if (Files.exists(path)) {
                location = path.toUri().toString();
            }
        } else {
            if (jarFile.getEntry(suffix) != null) {
                location = "jar:" + uri + "!/" +  subDirectory + "/" + suffix;
            }
        }
        //System.out.println("@locate " + location);
        return location == null ? null : new URL(location);

    }

    public void close() throws IOException {

        if (jarFile != null) {
            jarFile.close();
        }

    }

}
