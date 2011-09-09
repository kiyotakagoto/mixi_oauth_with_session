package tester.api.mixi;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.net.URLEncoder;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import tester.api.mixi.util.HttpUtils;

import net.arnx.jsonic.JSON;

public class OAuthMixiService extends HttpServlet {
    private static final String CLIENT_ID          = "[YOUR_CRIENT_ID]";
    private static final String CLIENT_SECRET      = "[YOUR_CRIENT_SECRET_ID]";
    private static final String REDIRECT_URL_MIXI  = "[YOUR_REDIRECT_URL";
    private static final String TOKEN_URL_MIXI     = "https://secure.mixi-platform.com/2/token";
    private final HttpUtils     httpUtils          = new HttpUtils();
    private static final Logger log                = Logger.getLogger(OAuthMixiService.class.getName());

    public void doPost(HttpServletRequest request, HttpServletResponse response )
        throws IOException {
        request.setCharacterEncoding( "UTF-8" );

        try {
            String code     = request.getParameter( "code" );
            String postData = this.createPostData( code );

            // http access
            HttpURLConnection urlConnection;
            String            returnedJson;
            urlConnection = this.httpUtils.getUrlConnection( "POST", OAuthMixiService.TOKEN_URL_MIXI, null );
            urlConnection = this.httpUtils.postData(urlConnection, postData);
            returnedJson  = this.httpUtils.getReturnedValue(urlConnection);
            urlConnection.disconnect();

            // decode json to HashMap and store the object in session
            HashMap     jsonObj;
            HttpSession session;
            jsonObj = JSON.decode( returnedJson );
            session = request.getSession( true );
            if ( null != jsonObj.get("error") ) {
                session.invalidate();
            }
            session.setAttribute("ACCESS_TOKENS", jsonObj);
            session.setMaxInactiveInterval(3600 * 24);

            // respond to client
            String access_token  = (String)jsonObj.get( "access_token" );
            if ( null != access_token ) {
                response.setStatus( HttpServletResponse.SC_OK); // 200
            }
            else {
                OAuthMixiService.log.severe("Cannot get access token from session\n");
                session.invalidate();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED); // 401
            }
        }
        catch ( Exception e ) {
            OAuthMixiService.log.severe("Failed complete oauth\n");
            OAuthMixiService.log.severe(e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String createPostData ( String code ) {
        try {
            String postData
                = "client_id="
                + OAuthMixiService.CLIENT_ID
                + "&client_secret="
                + OAuthMixiService.CLIENT_SECRET
                + "&grant_type=authorization_code"
                + "&code=" + code
                + "&redirect_uri=" + URLEncoder.encode( OAuthMixiService.REDIRECT_URL_MIXI, "UTF-8" )
                ;

            return postData;
        }
        catch ( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
}
