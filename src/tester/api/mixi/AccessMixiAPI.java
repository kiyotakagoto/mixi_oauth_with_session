package tester.api.mixi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.arnx.jsonic.JSON;
import tester.api.mixi.util.HttpUtils;

public class AccessMixiAPI extends HttpServlet {
    private static final String TOKEN_URL_MIXI     = "https://secure.mixi-platform.com/2/token";
    private static final String CLIENT_ID          = "[YOUR_CLIENT_ID]";
    private static final String CLIENT_SECRET      = "[YOUR_CLIENT_SECRET_ID]";
    private static final int    ERROR_INVALID_REQUEST    = 0;
    private static final int    ERROR_INVALID_TOKEN      = 1;
    private static final int    ERROR_EXPIRED_TOKEN      = 2;
    private static final int    ERROR_INSUFFICIENT_SCOPE = 3;
    private static final int    ERROR_INVALID_GRANT      = 4; // expired refresh_token
    private static final int    ERROR_UNKNOQN_ERROR      = 5;
    private static final int    ERROR_URI_NOT_FOUND      = 6;
    private static final int    ERROR_URI_FORBIDDEN      = 7;
    private static final int    ERROR_METHOD_NOT_ALLOWED = 8;
    private static final int    ERROR_NOT_IMPLEMENTED    = 9; 
    private final HttpUtils     httpUtils          = new HttpUtils();
    private static final int    APITYPE_VOICE_COMMENT_POST     = 6;
    private static final int    API_RETRY_LIMIT                = 2;
    private static final Logger log                = Logger.getLogger(OAuthMixiService.class.getName());

    @Override
    public void doGet( HttpServletRequest request, HttpServletResponse response )
        throws IOException {
        request.setCharacterEncoding( "UTF-8" );
        response.setCharacterEncoding( "UTF-8" );
        
        // get session data containing access_token, refresh_token, etc.
        HttpSession session = this._checkSession(request, response);
        this._tryToAccessApi(response, request, session, "GET");
    }
    @Override
    public void doPost( HttpServletRequest request, HttpServletResponse response ) 
        throws IOException {
        request.setCharacterEncoding( "UTF-8" );
        response.setCharacterEncoding( "UTF-8" );
        
        // get session data containing access_token, refresh_token, etc.
        HttpSession session = this._checkSession(request, response);
        this._tryToAccessApi(response, request, session, "POST");
    }
    /**
     * セッションが存在してたらセッション返し、存在してなかったら（期限切れなどで）エラー返す。
     * @param request
     * @param response
     * @return
     */
    private HttpSession _checkSession( HttpServletRequest request, HttpServletResponse response ) {
        HttpSession session = request.getSession( false );
        if ( null == session ) {
            try {
                response.sendError( HttpServletResponse.SC_UNAUTHORIZED );
            }
            catch (IOException e) {
                AccessMixiAPI.log.severe("Failed complete oauth\n");
                AccessMixiAPI.log.severe( e.getMessage() );
            }
        }
        return session;
    }
    /**
     * APIへアクセス。access_tokenの期限切れ止まらないよう、API_RETRY_LIMIT回だけ繰り返せるようになっている。
     * @param response
     * @param request
     * @param session
     */
    private void _tryToAccessApi (HttpServletResponse response, HttpServletRequest request, HttpSession session, String method) {
        // access api
        int     remainingTryNum = AccessMixiAPI.API_RETRY_LIMIT;
        HashMap sessionData     = (HashMap)session.getAttribute( "ACCESS_TOKENS" );
        do {
            try {
                HttpURLConnection urlConnection
                    = this._accessApi(session, request, method);
                int statusCode = urlConnection.getResponseCode();
		
                if ( 200 == statusCode ) {
                    String returnedJson = this.httpUtils.getReturnedValue(urlConnection);
                    urlConnection.disconnect();
                    this._respondSuccessContent(returnedJson, response);
                    break;
                }
                else if ( 401 == statusCode ) {
                    // トークン関連のエラーの場合は、レスポンスヘッダの WWW-Authenticate に何らかのエラーメッセージが入る。
                    // refresh_tokenの期限切れの場合、WWW-Authenticate ヘッダはなく、レスポンスボディにJSON形式でエラー文が入る。それらを見分ける。
                    int status_of_401 = this._inspectErrorCause(urlConnection);
    
                    switch ( status_of_401 ) {
                    case AccessMixiAPI.ERROR_EXPIRED_TOKEN :
                        this._refreshToken( (String)sessionData.get("refresh_token"), session, response);
                        break;
                    case AccessMixiAPI.ERROR_INSUFFICIENT_SCOPE :
                    case AccessMixiAPI.ERROR_INVALID_REQUEST    :
                        response.sendError( HttpServletResponse.SC_FORBIDDEN );
                        break;
                    default :
                        session.invalidate();
                        response.sendError( HttpServletResponse.SC_UNAUTHORIZED );
                        break;
                    }
                    break;
                }
                else if ( 403 == statusCode ) {
                    response.sendError( HttpServletResponse.SC_FORBIDDEN );
                    break;
                }
                else if ( 404 == statusCode ) {
                    response.sendError( HttpServletResponse.SC_NOT_FOUND );
                    break;
                }
                else if ( 405 == statusCode ) {
                    response.sendError( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
                    break;
                }
                else if ( 501 == statusCode ) {
                    response.sendError( HttpServletResponse.SC_NOT_IMPLEMENTED );
                    break;
                }
                else {
                    break;
                }
            }
            catch ( Exception e ) {
                AccessMixiAPI.log.severe("APIアクセス失敗");
                AccessMixiAPI.log.severe( e.getMessage() );
            }
            --remainingTryNum;
        } while ( remainingTryNum > 0 );
    }
    /**
     * APIにアクセスして、結果をハッシュマップとして返す。
     * @param session
     * @param request
     * @return
     */
    private HttpURLConnection _accessApi(HttpSession session, HttpServletRequest request, String method) {
        HashMap sessionData      = (HashMap)session.getAttribute( "ACCESS_TOKENS" );
        HashMap httpRequestParam = this.createUriParamObj(request);
        String  api_uri          = (String)httpRequestParam.get("api");

        try {
            HttpURLConnection urlConnection
                = this._doHTTPAccess(
                        URLDecoder.decode(api_uri, "UTF-8"),
                        (String)sessionData.get("access_token"),
                        method
                        );
            return urlConnection;
        }
        catch ( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * 正常取得できたAPIの結果を、JSONとしてレスポンスボディに入れて返す。
     * @param responseBody
     * @param response
     */
    private void _respondSuccessContent( String responseBody, HttpServletResponse response) {
        try {
            response.setContentType("application/json");
            response.getWriter().print( responseBody );
        }
        catch ( IOException e ) {
            e.printStackTrace();
        }
    }
    /**
     * リクエストボディに何らかの指定がいるAPIであるかどうかを判定（コメント投稿用のAPIなどがそれに相当）
     * @param apiType
     * @return
     */
    private boolean _thisApiNeedRequestBody( int apiType ) {
        switch ( apiType ) {
        case AccessMixiAPI.APITYPE_VOICE_COMMENT_POST :
            return true;
        default :
            return false;
        }
    }
    /**
     * APIに投げるリクエストボディを作成。今のところコメント投稿用APIにのみ対応。
     * @param param
     * @param apiType
     * @return
     */
    private String _createRequestBody(HashMap param, int apiType) {
        if ( apiType == AccessMixiAPI.APITYPE_VOICE_COMMENT_POST ) {
            return "text=" + (String)param.get("text");
        }
        else {
            return null;
        }
    }
    /**
     * APIへのHTTPアクセスの下準備をする。
     * @param uri
     * @param accessToken
     * @param method
     * @return
     */
    private HttpURLConnection _doHTTPAccess( String uri, String accessToken, String method ) {
        
        // prepare for http access
        HttpURLConnection       urlConnection;
        String                  returnedJson       = null;
        HashMap<String, String> headers            = new HashMap<String, String>();
        String                  authorizationValue = "OAuth " + accessToken;
        
        headers.put("Authorization", authorizationValue);
        
        // http access
        urlConnection = this.httpUtils.getUrlConnection(method, uri, headers);

        return urlConnection;
    }
    /**
     * アクセストークンの期限が切れた際に、refresh_tokenでアクセストークンを再取得する。
     * @param refreshToken
     * @param session
     */
    private void _refreshToken ( String refreshToken, HttpSession session, HttpServletResponse response ) {
        try{
            // prepare for http request
            String postData
                = "client_id="
                + AccessMixiAPI.CLIENT_ID
                + "&client_secret="
                + AccessMixiAPI.CLIENT_SECRET
                + "&grant_type=refresh_token"
                + "&refresh_token=" + refreshToken
                ;
            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put("Content-Type", "application/x-www-form-urlencoded" );
            
            // http access
            HttpURLConnection urlConnection;
            String            returnedJson;
            urlConnection = this.httpUtils.getUrlConnection("POST", AccessMixiAPI.TOKEN_URL_MIXI, headers);
            urlConnection = this.httpUtils.postData(urlConnection, postData);
	    
            if ( 200 == urlConnection.getResponseCode() ) {
                returnedJson = this.httpUtils.getReturnedValue(urlConnection);
                session.setAttribute("ACCESS_TOKENS", JSON.decode( returnedJson ));
            }
            else {
                try {
                    session.invalidate();
                    response.sendError( HttpServletResponse.SC_UNAUTHORIZED, "認証を継続できませんでした。もう一度はじめから認証をしてください。" );
                    System.exit( 0 );
                }
                catch (IOException e) {
                    session.invalidate();
                    e.printStackTrace();
                }
            }
        }
        catch ( IOException e ) {
            e.printStackTrace();
        }
    }
    /**
     * レスポンスヘッダのWWW-Authenticateの内容から、どういうエラー内容かを判定する。
     * @param WWW_AuthenticateValue
     * @return
     */
    private int _inspectErrorCause ( HttpURLConnection urlConnection ) {
	
        String WWW_AuthenticateValue = urlConnection.getHeaderField("WWW-Authenticate");
	
        // refresh_tokenの期限切れ
        if ( null == WWW_AuthenticateValue ) {
            return AccessMixiAPI.ERROR_INVALID_GRANT;
        }
        // そのほかの関連のエラー
        else {
            String[] WWW_Authenticate_header = WWW_AuthenticateValue.split("=");
            String errorMsg = WWW_Authenticate_header[1];

            int status = errorMsg.equals("'invalid_request'")    ? AccessMixiAPI.ERROR_INVALID_REQUEST
                       : errorMsg.equals("'invalid_token'")      ? AccessMixiAPI.ERROR_INVALID_TOKEN
                       : errorMsg.equals("'expired_token'")      ? AccessMixiAPI.ERROR_EXPIRED_TOKEN
                       : errorMsg.equals("'insufficient_scope'") ? AccessMixiAPI.ERROR_INSUFFICIENT_SCOPE
                       :                                           AccessMixiAPI.ERROR_UNKNOQN_ERROR
                       ;
         
            return status;
        }
    }
    /**
     * クライアントがURIパラメータとして投げてきたパラメータたちを、以下のようなhashmapにまとめる。
     * {
     *     parameter_name1 => parameter_value1,
     *     parameter_name2 => parameter_value2,
     *     ...
     * }
     * @param request
     * @return
     */
    private HashMap createUriParamObj (HttpServletRequest request) {
        HashMap uriParamObj = new HashMap();
        
        Enumeration<String> paramNames = request.getParameterNames();
        while ( paramNames.hasMoreElements() ) {
            String paramName = paramNames.nextElement();
            uriParamObj.put( paramName, request.getParameter(paramName) );
        }
        return uriParamObj;
    }
}
