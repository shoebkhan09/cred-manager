package org.gluu.credmanager.misc;

import org.apache.commons.beanutils.BeanMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by jgomer on 2017-07-07.
 */
public class Utils {

    private static Logger logger = LogManager.getLogger(Utils.class);

    public static Optional<String> stringOptional(String string){
        return Optional.ofNullable(string).map(str -> str.length()==0 ? null : str);
    }

    public static <T> Optional<T[]> arrayOptional(T[] array){
        return Optional.ofNullable(array).map(arr -> arr.length==0 ? null : arr);
    }

    public static boolean stringContains(String string, String value, boolean ci){
        string=Optional.ofNullable(string).orElse("");
        string=ci ? string.toLowerCase() : string;
        value=ci ? value.toLowerCase() : value;
        return string.contains(value);
    }

    public static boolean stringContains(String string, String value){
        return stringContains(string, value, false);
    }

    public static void nullEmptyLists(Object object){

        BeanMap map=new BeanMap(object);
        for (Object key : map.keySet()){
            String strKey=key.toString();
            if (map.getType(strKey).isAssignableFrom(List.class)){
                logger.debug("Found list {} in object", strKey);
                List list=(List) map.get(key);
                if (list!=null && list.size()==0) {     //Replace empty list by nulled list
                    map.put(key, null);
                    logger.debug("Empty list {} became null", strKey);
                }
            }
        }

    }

    public static <T, C extends Collection<T>> void emptyNullLists (Object object, Supplier<C> factory){

        BeanMap map=new BeanMap(object);
        for (Object key : map.keySet()){
            String strKey=key.toString();
            if (map.getType(strKey).isAssignableFrom(List.class)){
                logger.debug("Found list {} in object", strKey);
                List list=(List) map.get(key);
                if (list==null) {     //Replace by empty list
                    List<T> empty=Collections.emptyList();
                    map.put(key, empty.stream().collect(Collectors.toCollection(factory)));
                    logger.debug("Null list {} became empty list", strKey);
                }
            }
        }
    }

    public static <R, T> int firstTrue(List<T> list, Function<? super T, ? extends R> map1, Function<? super R, ? extends Boolean> map2){
        return list.stream().map(map1).map(map2).collect(Collectors.toList()).indexOf(true);
    }

    public static <R, T> List<R> mapCollectList(List<T> list, Function<? super T, ? extends R> map){
        return list.stream().map(map).collect(Collectors.toList());
    }

    public static <R, T> List<R> mapSortCollectList(List<T> list, Function<? super T, ? extends R> map){
        return list.stream().map(map).sorted().collect(Collectors.toList());
    }
/*
    public Reader readerFromFile(String fileName, Charset cs) throws FileNotFoundException{
        return new InputStreamReader(new FileInputStream(fileName), cs);
    }

    Properties tmpProp = new Properties();
            tmpProp.load(new Utils().readerFromFile(oxLdapOpt.get(), DEFAULT_CHARSET));*/


        /*

        <!-- Network -->
        <dependency>
            <groupId>org.apache.httpcomponents</groupId>
            <artifactId>httpclient</artifactId>
            <version>4.5.3</version>
        </dependency>
        <dependency>
            <groupId>org.apache.httpcomponents</groupId>
            <artifactId>httpcore</artifactId>
            <version>4.4.6</version>
        </dependency>
        CloseableHttpClient httpclient =HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(OIDCEndpointURL);
        HttpEntity entity=httpclient.execute(httpGet).getEntity();
    */
}
