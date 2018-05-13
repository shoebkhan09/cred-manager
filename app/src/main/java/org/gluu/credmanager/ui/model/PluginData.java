/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.model;

import org.pf4j.PluginDescriptor;

import java.util.List;

/**
 * @author jgomer
 */
public class PluginData {

    private String state;
    private String path;
    private List<String> extensions;
    private PluginDescriptor descriptor;

    public String getState() {
        return state;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public String getPath() {
        return path;
    }

    public PluginDescriptor getDescriptor() {
        return descriptor;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setDescriptor(PluginDescriptor descriptor) {
        this.descriptor = descriptor;
    }

}
