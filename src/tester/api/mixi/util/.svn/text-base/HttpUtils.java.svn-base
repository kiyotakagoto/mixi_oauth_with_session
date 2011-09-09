package tester.api.mixi.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Set;


public class HttpUtils {
    /**
     * config HttpURLConnection instance and return the instance.   
     * @param method
     * @param uri
     * @param headers
     * @return
     */
    public HttpURLConnection getUrlConnection ( String method, String uri, HashMap<String,String> headers ) {
        URL tokenUrl;
        try {
            // basic config
            tokenUrl = new URL( uri );
            HttpURLConnection urlConnection = (HttpURLConnection)tokenUrl.openConnection();
            boolean doOutput = method.equals("POST") ? true
                             : false
                             ;
            urlConnection.setDoOutput(       doOutput );
            urlConnection.setRequestMethod(  method   );
            urlConnection.setReadTimeout(    60000    );
            urlConnection.setConnectTimeout( 60000    );
            
            // set headers
            if ( null != headers ) {
                Set<String> headerNames = headers.keySet();
                for ( String headerName : headerNames ) {
                    urlConnection.setRequestProperty( headerName, headers.get(headerName) );
                }
            }
            
            urlConnection.connect();
            
            return urlConnection;
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
        catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * post DATA by HTTP POST method.
     * @param urlConnection
     * @param postData
     * @return
     */
    public HttpURLConnection postData( HttpURLConnection urlConnection, String postData ) {
        OutputStream outputStream;
        try {
            if ( urlConnection.getDoOutput() ) {
                outputStream = urlConnection.getOutputStream();
                PrintStream printStream = new PrintStream( outputStream );
                printStream.print( postData ); // send data with POST method
                printStream.close();
            }
            return urlConnection;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    /**
     * get returned String value.
     * @param urlConnection
     * @return
     */
    public String getReturnedValue( HttpURLConnection urlConnection ) {
        InputStream inputStream;
        try {
            inputStream = urlConnection.getInputStream();
            BufferedReader reader = new BufferedReader( new InputStreamReader( inputStream, "UTF-8" ) );
            String s;
            String returnedJson = "";
            while ( (s = reader.readLine()) != null ) {
                returnedJson += s;
            }
            reader.close();
            
            return returnedJson;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
