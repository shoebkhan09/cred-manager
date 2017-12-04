package org.gluu.credmanager.misc;

import org.apache.commons.beanutils.BeanMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.credential.FidoDevice;
import org.xdi.model.SimpleCustomProperty;
import org.zkoss.util.Pair;

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

    public static <T> Optional<List<T>> listOptional(List<T> list){
        return Optional.ofNullable(list).map(l -> l.size()==0 ? null : l);
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

    /**
     * This method takes an arbitrary object and using introspection (via commons-beanutils) detects fields whose type is
     * list, and if found them empty, they are set to null. In other words, it replaces empty lists by nulled lists.
     * This was coded to make org.gluu.site.ldap.persistence.LdapEntryManager happier
     * @param object Object to process. Changes are effective in place
     */
    public static void nullEmptyLists(Object object){
        //TODO: implement this using mapper.convertValue(object, Map.class)?
        BeanMap map=new BeanMap(object);
        for (Object key : map.keySet()){
            String strKey=key.toString();
            if (map.getType(strKey).isAssignableFrom(List.class)){
                logger.trace("nullEmptyLists. Found list {} in object", strKey);
                List list=(List) map.get(key);
                if (list!=null && list.size()==0) {     //Replace empty list by nulled list
                    map.put(key, null);
                    logger.trace("nullEmptyLists. Empty list {} became null", strKey);
                }
            }
        }

    }

    /**
     * This method is the converse of nullEmptyLists: takes and object, and find members of it that are null lists and
     * changes them to empty lists. To instatiate the empty lists, it uses a Supplier instance
     * @param object Arbitrary object to change
     * @param factory A supplier to instantiate empty lists
     * @param <T>
     * @param <C>
     */
    public static <T, C extends Collection<T>> void emptyNullLists(Object object, Supplier<C> factory){

        //TODO: implement this using mapper.convertValue(object, Map.class)?
        BeanMap map=new BeanMap(object);
        for (Object key : map.keySet()){
            String strKey=key.toString();
            if (map.getType(strKey).isAssignableFrom(List.class)){
                logger.trace("emptyNullLists. Found list {} in object", strKey);
                List list=(List) map.get(key);
                if (list==null) {     //Replace by empty list
                    List<T> empty=Collections.emptyList();
                    map.put(key, empty.stream().collect(Collectors.toCollection(factory)));
                    logger.trace("emptyNullLists. Null list {} became empty list", strKey);
                }
            }
        }
    }

    //Takes a List, applies a map operation on it, then applies a second map turning elements into booleans, and returns the index of first true occurrence
    public static <R, T> int firstTrue(List<T> list, Function<? super T, ? extends R> map1, Function<? super R, ? extends Boolean> map2){
        return list.stream().map(map1).map(map2).collect(Collectors.toList()).indexOf(true);
    }

    //Takes a List, applies a map operation on it, and then uses the default collector to return a new list
    public static <R, T> List<R> mapCollectList(List<T> list, Function<? super T, ? extends R> map){
        return list.stream().map(map).collect(Collectors.toList());
    }

    //Takes a List, applies a map on it, and then sorts according to natural order of elements, and finally collects
    public static <R, T> List<R> mapSortCollectList(List<T> list, Function<? super T, ? extends R> map){
        return list.stream().map(map).sorted().collect(Collectors.toList());
    }

    //Takes a list of SimpleCustomProperty instances, and creates a Map of (key,val) pairs where key is value1 and val is value2
    public static Map<String, String> getScriptProperties(List<SimpleCustomProperty> properties) {
        Map<String, String> propsMap = new HashMap<>();
        properties.forEach(prop -> propsMap.put(prop.getValue1().toLowerCase(), prop.getValue2()));
        return propsMap;
    }

    /**
     * Chooses one device from a list of devices, such that its creation time is the closest to the timestamp given
     * @param devices A non-null list of fido devices
     * @param time A timestamp as milliseconds elapsed from the "epoch"
     * @param <T>
     * @return The best matching device (only devices added before the time supplied are considered). Null if no suitable
     * device could be found
     */
    public static <T extends FidoDevice> T getRecentlyCreatedDevice(List<T> devices, long time){

        long diffs[]=devices.stream().mapToLong(key -> time-key.getCreationDate().getTime()).toArray();

        logger.trace("getRecentlyCreatedDevice. diffs {}", Arrays.asList(diffs));
        //Search for the smallest time difference
        int i;
        Pair<Long, Integer> min=new Pair<>(Long.MAX_VALUE, -1);
        //it always holds that diffs.length==devices.size()
        for (i=0;i<diffs.length;i++)
            if (diffs[i]>=0 && min.getX()>diffs[i])  //Only search non-negative differences
                min=new Pair<>(diffs[i], i);

        i=min.getY();
        return i==-1 ? null : devices.get(i);

    }

}
