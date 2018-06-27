/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.misc;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jmimemagic.Magic;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.util.Clients;

import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.enterprise.inject.spi.CDI;

/**
 * @author jgomer
 */
public final class Utils {

    private static Logger LOG = LoggerFactory.getLogger(Utils.class);
    private static ObjectMapper MAPPER = new ObjectMapper();

    public static final int FEEDBACK_DELAY_SUCC = 1500;
    public static final int FEEDBACK_DELAY_ERR = 3000;

    private Utils() { }

    public static boolean onWindows() {
        return System.getProperty("os.name").toLowerCase().matches(".*win.*");
    }

    public static boolean isEmpty(String string) {
        return !isNotEmpty(string);
    }

    public static boolean isNotEmpty(String string) {
        return Optional.ofNullable(string).map(String::length)
                .flatMap(i -> i > 0 ? Optional.of(i) : Optional.empty()).isPresent();
    }

    public static <T> boolean isEmpty(T[] array) {
        return !isNotEmpty(array);
    }

    public static <T> boolean isNotEmpty(T[] array) {
        return Optional.ofNullable(array).map(arr -> arr.length)
                .flatMap(i -> i > 0 ? Optional.of(i) : Optional.empty()).isPresent();
    }

    public static <T> boolean isEmpty(Collection<T> collection) {
        return !isNotEmpty(collection);
    }

    public static <T> boolean isNotEmpty(Collection<T> collection) {
        return Optional.ofNullable(collection).map(Collection::size)
                .flatMap(i -> i > 0 ? Optional.of(i) : Optional.empty()).isPresent();
    }

    public static <K, V> boolean isEmpty(Map<K, V> map) {
        return !isNotEmpty(map);
    }

    public static <K, V> boolean isNotEmpty(Map<K, V> map) {
        return Optional.ofNullable(map).map(Map::size)
                .flatMap(i -> i > 0 ? Optional.of(i) : Optional.empty()).isPresent();
    }

    public static boolean isJarFile(Path path) {
        return path.toString().toLowerCase().endsWith(".jar") && Files.isRegularFile(path);
    }

    public static boolean isClassFile(Path path) {
        return path.toString().endsWith(".class") && Files.isRegularFile(path);
    }

    public static <T> T managedBean(Class<T> clazz) {
        return CDI.current().select(clazz).get();
    }

    public static String jsonFromObject(Object obj) {

        String json;
        try {
            json = MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            json = "{}";
        }
        return json;

    }

    public static String getImageDataUriEncoding(byte[] bytes, String hintName) {

        String mime = null;
        String encodedImg = Base64.getEncoder().encodeToString(bytes);
        try {
            mime = Magic.getMagicMatch(bytes).getMimeType();
        } catch (Exception e) {
            if (hintName != null) {
                mime = URLConnection.guessContentTypeFromName(hintName);
            }
        }
        if (mime == null) {
            mime = "";
            LOG.trace("Cannot infer mime type of image");
        } else {
            LOG.trace("Using mime {}", mime);
        }
        return String.format("data:%s;base64,%s", mime, encodedImg);

    }

    public static void showMessageUI(boolean success) {
        showMessageUI(success, Labels.getLabel(success ? "general.operation_completed" : "general.error.general"));
    }

    public static void showMessageUI(boolean success, String msg) {
        showMessageUI(success, msg, "middle_center");
    }

    public static void showMessageUI(boolean success, String msg, String position) {
        if (success) {
            Clients.showNotification(msg, Clients.NOTIFICATION_TYPE_INFO, null, position, FEEDBACK_DELAY_SUCC);
        } else {
            Clients.showNotification(msg, Clients.NOTIFICATION_TYPE_WARNING, null, position, FEEDBACK_DELAY_ERR);
        }
    }

    public static Object cloneObject(Object obj) {

        Object result = null;
        try {
            result = BeanUtils.cloneBean(obj);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        return result;

    }

    public static <T> List<T> listfromArray(T[] array) {
        if (isEmpty(array)) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(array);
        }
    }

    public static String[] stringArrayFrom(String value) {
        return Optional.ofNullable(value).map(v -> new String[] { v }).orElse(new String[0]);
    }

    public static boolean hostAvailabilityCheck(SocketAddress address, int timeout) {

        boolean available = false;
        try (Socket socket = new Socket()) {
            socket.connect(address, timeout);
            available = true;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        return available;
    }

    public static boolean isValidUrl(String strUrl) {

        boolean valid = false;
        try {
            URL url = new URL(strUrl);
            valid = isNotEmpty(url.getHost());
        } catch (Exception e) {
            //Intentionally left empty
        }
        if (!valid) {
            LOG.warn("Error validating url: {}", strUrl);
        }
        return valid;

    }

    //Takes a List, applies a map turning elements into booleans, and returns the index of first true occurrence
    public static <T> int firstTrue(List<T> list, Function<? super T, ? extends Boolean> map){
        return list.stream().map(map).collect(Collectors.toList()).indexOf(true);
    }

}
