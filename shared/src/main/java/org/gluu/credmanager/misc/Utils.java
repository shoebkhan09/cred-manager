/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.misc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.inject.spi.CDI;

/**
 * @author jgomer
 */
public final class Utils {

    private static ObjectMapper MAPPER = new ObjectMapper();

    private Utils() { }

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

}
