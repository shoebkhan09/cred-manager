package org.gluu.credmanager.misc;

import java.util.Optional;

/**
 * Created by jgomer on 2017-07-07.
 */
public class Utils {

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
