/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * REST notes
 * 
 * POST
    * HTTP.POST can be used when the client is sending data to the server and the server
    * will decide the URI for the newly created resource. The POST method is used 
    * to request that the origin server accept the entity enclosed in the request
    * as a new subordinate of the resource identified by the Request-URI 
    * in the Request-Line.

 * PUT
    * HTTP.PUT can be used when the client is sending data to the the server and
    * the client is determining the URI for the newly created resource. The PUT method 
    * requests that the enclosed entity be stored under the supplied Request-URI. If 
    * the Request-URI refers to an already existing resource, the enclosed entity
    * SHOULD be considered as a modified version of the one residing on the origin
    * server. If the Request-URI does not point to an existing resource, 
    * and that URI is capable of being defined as a new resource by the requesting 
    * user agent, the origin server can create the resource with that URI.
    * It is most-often utilized for update capabilities, PUT-ing to a known resource
    * URI with the request body containing the newly-updated representation of the 
    * original resource.

 * PATCH
    * HTTP.PATCH can be used when the client is sending one or more changes to be
    * applied by the the server. The PATCH method requests that a set of changes described 
    * in the request entity be applied to the resource identified by the Request-URI.
    * The set of changes is represented in a format called a patch document.
    * Submits a partial modification to a resource. If you only need to update one
    * field for the resource, you may want to use the PATCH method.
 * 
 */

package edu.slu.action;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.opensymphony.xwork2.ActionSupport;
import edu.slu.common.Constant;
import edu.slu.mongoEntity.AcceptedServer;
import edu.slu.service.MongoDBService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.types.ObjectId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import javax.servlet.http.HttpSession;
import net.sf.json.JSONException;


/**
 * @author hanyan && bhaberbe
 * All the actions hit as an API like ex. /saveNewAnnotation.action
 * This implementation follows RESTFUL standards.  If you make changes, please adhere to this standard.

 */
public class AnnotationAction extends ActionSupport implements ServletRequestAware, ServletResponseAware{
    private String content;
    private String oid;
    private AcceptedServer acceptedServer;
    private MongoDBService mongoDBService;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringBuilder bodyString;
    private BufferedReader bodyReader;
    private PrintWriter out;
    final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Write error to response.out.  The methods that call this function handle quitting, this just writes the error because of the quit. 
     * @param msg The message to show the user
     * @param status The HTTP response status to return
     */
    public void send_error(String msg, int status){
        // TODO: @theHabes the naming of this seems a little off. It does not
        // send the error, just writes it to the response. Also the casing feels
        // non-standard.
        // @cubap @agree it was a weird method to implement so I left it weird.  It should be renamed, all other methods are camelCased. 
        // maybe needs better documentation to clarify.
        JSONObject jo = new JSONObject();
        jo.element("code", status);
        jo.element("message", msg);
        try {
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json");
            response.setStatus(status);
            out = response.getWriter();
            out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(jo));
            out.write(System.getProperty("line.separator"));
        } catch (IOException ex) {
            Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Add the __rerum properties object to a given JSONObject. If __rerum already exists you need to update certain values.  See below.
     * Properties for consideration are:
     *   APIversion        —1.0.0
     *   history.prime     —if it has an @id, import from that, else "root"
     *   history.next      —always [] (or null, perhaps)
     *   history.previous  —if it has an @id, @id
     *   releases.previous —if it has an @id, import from that, else null
     *   releases.next     —always [] (or null, perhaps)
     *   generatedBy       —set to the @id of the public agent of the API Key.
     *   createdAt         —"addedTime" timestamp in milliseconds
     *   isOverwritten, isReleased   —always null
     * 
     * @param received A potentially optionless JSONObject from the Mongo Database (not the user).  This prevents tainted __rerum's
     * @return configuredObject The same object that was recieved but with the proper __rerum options.  This object is intended to be saved as a new object (@see versioning)
     */
    public JSONObject configureRerumOptions(JSONObject received){
        JSONObject configuredObject = received;
        JSONObject received_options;
        try{
            //If this is an update, the object will have __rerum
            received_options = received.getJSONObject("__rerum"); 
        }
        catch(Exception e){ 
            //otherwise, it is a new save or an update on an object without the __rerum property
            received_options = new JSONObject();
        }
        JSONObject history = new JSONObject();
        JSONObject releases = new JSONObject();
        JSONObject rerumOptions = new JSONObject();
        String history_prime = "";
        String history_previous = "";
        String releases_previous = "";
        String[] emptyArray = new String[0];
        rerumOptions.element("APIversion", "1.0.0");
        rerumOptions.element("createdAt", System.currentTimeMillis());
        rerumOptions.element("isOverwritten", "");
        rerumOptions.element("isReleased", "");
        if(received_options.containsKey("history")){
            history = received_options.getJSONObject("history");
            history_prime = history.getString("prime");
            history_previous = received.getString("@id");
        }
        else{
            history_prime = "root";
            history_previous = "";
        }
        if(received_options.containsKey("releases")){
            releases = received.getJSONObject("releases");
            releases_previous = releases.getString("previous");
        }
        else{
            releases_previous = "";
            
        }
        releases.element("next", emptyArray);
        history.element("next", emptyArray);
        history.element("previous", history_previous);
        history.element("prime", history_prime);
        releases.element("previous", releases_previous);
        rerumOptions.element("history", history);
        rerumOptions.element("releases", releases);      
        //The access token is in the header  "Authorization: Bearer {YOUR_ACCESS_TOKEN}"
        //HttpResponse<String> response = Unirest.post("https://cubap.auth0.com/oauth/token") .header("content-type", "application/json") .body("{\"grant_type\":\"client_credentials\",\"client_id\": \"WSCfCWDNSZVRQrX09GUKnAX0QdItmCBI\",\"client_secret\": \"8Mk54OqMDqBzZgm7fJuR4rPA-4T8GGPsqLir2aP432NnmG6EAJBCDl_r_fxPJ4x5\",\"audience\": \"https://cubap.auth0.com/api/v2/\"}") .asString(); 
        rerumOptions.element("generatedBy",""); //TODO get the @id of the public agent of the API key
        configuredObject.element("__rerum", rerumOptions); //.element will replace the __rerum that is there OR create a new one
        return configuredObject; //The mongo save/update has not been called yet.  The object returned here will go into mongo.save or mongo.update
    }
    
    /**
     * Update the history.next property of an object.  This will occur because updateAnnotation will create a new object from a given object, and that
     * given object will have a new next value of the new object.  Watch out for missing __rerum or malformed __rerum.history
     * 
     * @param idForUpdate the @id of the object whose history.next needs to be updated
     * @param newNextID the @id of the newly created object to be placed in the history.next array.
     * @return Boolean altered true on success, false on fail
     */
    public boolean alterHistoryNext (String idForUpdate, String newNextID){
        Boolean altered = false;
        BasicDBObject query = new BasicDBObject();
        query.append("@id", idForUpdate);
        DBObject myAnno = mongoDBService.findOneByExample(Constant.COLLECTION_ANNOTATION, query);
        JSONObject annoToUpdate = JSONObject.fromObject(myAnno);
        if(null != myAnno){
            try{
                JSONArray history_next = annoToUpdate.getJSONObject("__rerum").getJSONObject("history").getJSONArray("next"); //JSONArray allows this to be a String[]
                history_next.add(newNextID); //Add as the last value in the array
                annoToUpdate.getJSONObject("__rerum").getJSONObject("history").element("next", history_next); //write back to the anno from mongo
                myAnno = (BasicDBList) JSON.parse(annoToUpdate.toString()); //make the JSONObject a DB object
                mongoDBService.update(Constant.COLLECTION_ANNOTATION, query, myAnno); //update in mongo
                altered = true;
            }
            catch(Exception e){ //__rerum array does not exist or history object malformed.  What should I do?
                //TODO check that this fails like we expect and does not stack.
                send_error("This object does not contain the proper history property.", HttpServletResponse.SC_CONFLICT);
            }
        }
        else{ //THIS IS A 404
            //TODO check that this fails like we expect and does not stack
            send_error("Object for history.next update not found...", HttpServletResponse.SC_NOT_FOUND);
        }
        return altered;
    }
    
    /**
     * Checks for appropriate RESTful method being used.
     * The action first comes to this function.  It says what type of request it 
     * is and checks the the method is appropriately RESTful.  Returns false if not and
     * the method that calls this will handle a false response;
     * @param http_request the actual http request object
     * @param request_type a string denoting what type of request this should be
     * @return Boolean indicating RESTfulness
     * @throws Exception 
    */
    public Boolean methodApproval(HttpServletRequest http_request, String request_type) throws Exception{
        String requestMethod = http_request.getMethod();
        boolean restful = false;
        switch(request_type){
            case "update":
                if(requestMethod.equals("PUT") || requestMethod.equals("PATCH")){
                    restful = true;
                }
                else{
                    send_error("Improper request method for updating, please use PUT or PATCH.", HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                }
            break;
            case "create":
                if(requestMethod.equals("POST")){
                    restful = true;
                }
                else{
                    send_error("Improper request method for creating, please use POST.", HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                }
            break;
            case "delete":
                if(requestMethod.equals("DELETE")){
                    restful = true;
                }
                else{
                    send_error("Improper request method for deleting, please use DELETE.", HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                }
            break;
            case "get":
                if(requestMethod.equals("GET")){
                    restful = true;
                }
                else{
                    send_error("Improper request method for reading, please use GET.", HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                }
            break;
            default:
                send_error("Improper request method for this type of request (unknown).", HttpServletResponse.SC_METHOD_NOT_ALLOWED);

            }  
        return restful;
    }
    
    /**
     * All actions come here to process the request body. We check if it is JSON
     * and pretty format it. Returns pretty stringified JSON or fail to null.
     * Methods that call this should handle requestBody==null as unexpected.
     * @param http_request Incoming request to check.
     * @return String of anticipated JSON format.
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     * @throws java.lang.Exception
     */
    public String processRequestBody(HttpServletRequest http_request) throws IOException, ServletException, Exception{
        
        String cType = http_request.getContentType();
        String requestBody;
        JSONObject complianceInfo = new JSONObject();
        if(cType.contains("application/json") || cType.contains("application/ld+json")) {
            bodyReader = http_request.getReader();
            bodyString = new StringBuilder();
            JSONObject test;
            JSONArray test2;
            String line;
            while ((line = bodyReader.readLine()) != null)
            {
              bodyString.append(line).append("\n");
            }
            requestBody = bodyString.toString();
            try{ 
              // JSON test
              test = JSONObject.fromObject(requestBody);
            }
            catch(Exception ex){ 
              // not a JSONObject; test for JSONArray
                try{
                    test2 = JSONArray.fromObject(requestBody);
                }
                catch(Exception ex2){
                    // not a JSONObject or a JSONArray. Throw error. 
                    send_error("The data passed was not valid JSON:\n"+requestBody, HttpServletResponse.SC_BAD_REQUEST);
                    requestBody = null;
                }
            }          
            // no-catch: Is either JSONObject or JSON Array
        }
        else { 
            send_error("Invalid Content-Type. Please use 'application/json' or 'application/ld+json'", HttpServletResponse.SC_BAD_REQUEST);
            requestBody = null;
        }
        //@cubap @theHabes TODO IIIF compliance handling on action objects
        /*
        if(null != requestBody){
            complianceInfo = checkIIIFCompliance(requestBody, "2.1");
            if(complianceInfo.getInt("okay") < 1){
                send_error(complianceInfo.toString(), HttpServletResponse.SC_CONFLICT);
                requestBody = null;
            }
        }
        */
        response.setContentType("application/json"); // We create JSON objects for the return body in most cases.  
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, PUT"); // FIXME: Consider adding OPTIONS
        return requestBody;
    }
    
    /**
     * @@@ The rerum options code is not applied here as this will be @deprecated
    *The batch save to intended to work with Broken Books, but could be applied elsewhere.  This batch will use the save() mongo function instead of insert() to determine whether 
    to do an update() or insert() for each item in the batch.  
    *    The content is from an HTTP request posting in an array filled with annotations to copy.  
     * @throws java.io.UnsupportedEncodingException
     * @throws javax.servlet.ServletException
    *    @see MongoDBAbstractDAO.bulkSaveMetadataForm(String collectionName, BasicDBList entity_array);
    *    @see MongoDBAbstractDAO.bulkSetIDProperty(String collectionName, BasicDBObject[] entity_array);
    */   
    public void batchSaveMetadataForm() throws UnsupportedEncodingException, IOException, ServletException, Exception{
        // TODO: @theHabes refactor name here. Also, just try-catch JSONArray.fromObject(processRequestBody(request))
        // since this is the content= free version.
        // @cubap @agree.  The naming is ancient and I did not unwrap the original try{JSONPARSE}{catch{JSONPARSE error}, processRequestBody does that for us.
        // My secondary thought for leaving it was in case an error occurred in JSON.accumulate or JSON.element, but this is unnecessary
        // This one will not be used as the standard bulk operation, it is meant to work specifically with brokenBooks
        
        // @cubap @agree. We can let the falsey state of processRequestBody() be the switch instead of the null check.  Keep the issue of
        // error stacking in the back of your mind as we develop this and remember we can't throw Exceptions
        
        Boolean approved = methodApproval(request, "create");
        if(approved){
            content = processRequestBody(request);
        }
        else{
            content = null;
        }
        if(null != content){
            JSONArray received_array = JSONArray.fromObject(content);
             // I think this is already handled in requestServerAuthenticationFilter, just commenting out for now
//            BasicDBObject serverQuery = new BasicDBObject();
//            serverQuery.append("ip", request.getRemoteAddr());
//            DBObject asdbo = mongoDBService.findOneByExample(Constant.COLLECTION_ACCEPTEDSERVER, serverQuery);
//            BasicDBObject asbdbo = (BasicDBObject) asdbo;

            BasicDBList dbo = (BasicDBList) JSON.parse(received_array.toString());
            JSONArray reviewedResources = new JSONArray();
            //if the size is 0, no need to bulk save.  Nothing is there.
            if(dbo.size() > 0){
                reviewedResources = mongoDBService.bulkSaveMetadataForm(Constant.COLLECTION_ANNOTATION, dbo);
            }
            else{
             //   Skipping bulk save on account of empty array.
            }
            JSONObject jo = new JSONObject();
            jo.element("code", HttpServletResponse.SC_CREATED);
            jo.element("reviewed_resources", reviewedResources);
            addLocationHeader(reviewedResources);
            try {
                out = response.getWriter();
                response.setStatus(HttpServletResponse.SC_CREATED);
                out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(jo));
            } catch (IOException ex) {
                Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    /**
     * Creates or appends Location header from @id.
     * Headers are attached and read from {@link #response}. 
     * @param obj  the JSON being returned to client
     * @see #addLocationHeader(net.sf.json.JSONArray) addLocationHeader(JSONArray)
     */
    private void addLocationHeader(JSONObject obj){
        String addLocation;
        String thisLocation = response.getHeader("Location");
        // Warning: if there are multiple "Location" headers only one will be returned
        // our practice of making a list protects from this.
        if (response.containsHeader("Location")) {
            // add to existing header
            addLocation = response.getHeader("Location").concat(",").concat(obj.getString("@id"));
        }
        else {
            // no header attached yet
            addLocation = obj.getString("@id");
        }
        response.setHeader("Location", addLocation);
    }

    /**
     * Creates or appends list of @ids to Location header.
     * Headers are attached and read from {@link #response}. 
     * @param arr  the JSON Array being returned to the client
     * @see #addLocationHeader(net.sf.json.JSONObject) addLocationHeader(JSONObject)
     */    
    private void addLocationHeader(JSONArray arr){
        for(int j=0; j<arr.size(); j++){
            addLocationHeader(arr.getJSONObject(j));
        }
    }

    /** 
        * TODO @see batchSaveMetadataForm.  Do both methods need to exist?  Combine if possible. This is the method we use for generic bulk saving.
        * Each canvas has an annotation list with 0 - infinity annotations.  A copy requires a new annotation list with the copied annotations and a new @id.
        * Mongo allows us to bulk save.  
        * The content is from an HTTP request posting in an array filled with annotations to copy.  
     * @throws java.io.UnsupportedEncodingException
     * @throws javax.servlet.ServletException
        * @see MongoDBAbstractDAO.bulkSaveFromCopy(String collectionName, BasicDBList entity_array);
        * @see MongoDBAbstractDAO.bulkSetIDProperty(String collectionName, BasicDBObject[] entity_array);
    */ 
    public void batchSaveFromCopy() throws UnsupportedEncodingException, IOException, ServletException, Exception{
        // TODO: refactor name here. This is the start of SmartObjects/subdocumenting
        // Also, just try-catch JSONArray.fromObject(processRequestBody(request))
        // since this is the content= free version.
        // @cubap @agree.  The naming is ancient and I did not unwrap the original try{JSONPARSE}{catch{JSONPARSE error}, processRequestBody does that for us.
        // My secondary thought for leaving it was in case an error occurred in JSON.accumulate or JSON.element, but this is unnecessary.
        // This will be what we use as our standard bulk operator.  Originally developed for T-PEN Newberry
        
        // @cubap @agree. We can let the falsey state of processRequestBody() be the switch instead of the null check.  Keep the issue of
        // error stacking in the back of your mind as we develop this and remember we can't throw Exceptions
       Boolean approved = methodApproval(request, "create");
        if(approved){
            content = processRequestBody(request);
        }
        else{
            content = null;
        }
        if(null != content){
            JSONArray received_array = JSONArray.fromObject(content);
            // I think this is already handled in requestServerAuthenticationFilter, just commenting out for now
//            BasicDBObject serverQuery = new BasicDBObject();
//            serverQuery.append("ip", request.getRemoteAddr());
//            DBObject asdbo = mongoDBService.findOneByExample(Constant.COLLECTION_ACCEPTEDSERVER, serverQuery);
//            BasicDBObject asbdbo = (BasicDBObject) asdbo;
            for(int b=0; b<received_array.size(); b++){ //Configure __rerum on each object
                JSONObject configureMe = received_array.getJSONObject(b);
                configureMe = configureRerumOptions(configureMe); //configure this object
                received_array.set(b, configureMe); //Replace the current iterated object in the array with the configured object
            }
            BasicDBList dbo = (BasicDBList) JSON.parse(received_array.toString()); //tricky cause can't use JSONArray here
            JSONArray newResources = new JSONArray();
            //if the size is 0, no need to bulk save.  Nothing is there.
            if(dbo.size() > 0){
                newResources = mongoDBService.bulkSaveFromCopy(Constant.COLLECTION_ANNOTATION, dbo);
            }
            else {
                // empty array
            }
            //bulk save will automatically call bulk update 
            JSONObject jo = new JSONObject();
            jo.element("code", HttpServletResponse.SC_CREATED);
            jo.element("new_resources", newResources);
            addLocationHeader(newResources);
            try {
                out = response.getWriter();
                response.setStatus(HttpServletResponse.SC_CREATED);
                out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(jo));
            } catch (IOException ex) {
                Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    /**
     * Servlet method to find all upstream versions of an object.
     * If this object is `prime`, it will be the only object in the array.
     * @param  http_request Servlet request for relatives
     * @throws Exception 
     */
    public void getAllAncestors(HttpServletRequest http_request) throws Exception{
        // TODO: @theHabes, this is waiting for something clever to happen.
        // This code is not correct at all, but pseudo-correct.
        List<DBObject> ls_versions = getAllVersions(http_request);
        // cubap: At this point, we have all the versions of the object (except maybe the
        // original?) and need to filter to the ones we want.
        // Getting the whole document is a mess, but if we get subdocuments of __rerum, 
        // we don't need to worry as much.
        
        JSONArray ancestors = getAllAncestors(ls_versions);
        try {
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setStatus(HttpServletResponse.SC_OK);
            out = response.getWriter();
            out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(ancestors));
        } 
        catch (IOException ex) {
            Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Filters ancestors upstream from `key object` until `prime`.
     * @param  ls_versions all the versions of the key object on all branches
     * @return array of objects
     */
    private JSONArray getAllAncestors(List<DBObject> ls_versions) {
        List<DBObject> ls_objects = null;
        // TODO: Iterate the List and find the original object. Then move from
        // _rerum.history.previous to _rerum.history.previous, building a new List
        // to return to the servlet. Stop at "root".
        JSONArray objects = JSONArray.fromObject(ls_objects);
        return objects;
    }
    
    /**
     * Filters for all versions downstream from `key object`.
     * @param  ls_versions all the versions of the key object on all branches
     * @return array of objects
     */
    private JSONArray getAllDescendants(List<DBObject> ls_versions) {
        List<DBObject> ls_objects = null;
        // TODO: Iterate the List and find the original object. Then move from
        // _rerum.history.next to _rerum.history.next, building a new List
        // to return to the servlet. Consider organizing tree in arrays.
        JSONArray objects = JSONArray.fromObject(ls_objects);
        return objects;
    }
    
    /**
     * Servlet method to find all downstream versions of an object.
     * If this object is the last, the return will be null.
     * @param  http_request Servlet request for relatives
     * @throws Exception 
     */
    public void getAllDescendants(HttpServletRequest http_request) throws Exception{
        // TODO: @theHabes, this is waiting for something clever to happen.
        // This code is not correct at all, but pseudo-correct.
        List<DBObject> ls_versions = getAllVersions(http_request);
        // cubap: At this point, we have all the versions of the object (except maybe the
        // original?) and need to filter to the ones we want.
        // Getting the whole document is a mess, but if we get subdocuments of __rerum, 
        // we don't need to worry as much.
        
        JSONArray descendants = getAllDescendants(ls_versions);
        try {
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setStatus(HttpServletResponse.SC_OK);
            out = response.getWriter();
            out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(descendants));
        } 
        catch (IOException ex) {
            Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Loads all derivative versions from the `prime` object. Used for filtering
     * in other methods. May be replaced later with more optimized logic.
     * @param  http_request Servlet request for relatives
     * @return All versions from the store of the object in the request
     * @throws Exception 
     */
    private List<DBObject> getAllVersions(HttpServletRequest http_request) throws Exception {
        List<DBObject> ls_versions = null;
        Boolean approved = methodApproval(request, "get");
        if(!methodApproval(request, "get")){
            // TODO: include link to API documentation in error response
            send_error("Unable to retrieve objects; wrong method type.", HttpServletResponse.SC_BAD_REQUEST);
            return ls_versions;
        }
        if(processRequestBody(request)==null){
            // TODO: include link to API documentation in error response
            send_error("Unable to retrieve objects; missing key object.", HttpServletResponse.SC_BAD_REQUEST);
            return ls_versions;
        }
        //content is set to body now
        JSONObject received = JSONObject.fromObject(content);
        // get reliable copy of key object
        BasicDBObject query = new BasicDBObject();
        
        // TODO: @theHabes, this is waiting for something clever to happen.
        // This code is not correct at all, but pseudo-correct.
        query.append("@id", new ObjectId(received.getString("__rerum.history.prime")));
        ls_versions = mongoDBService.findByExample(Constant.COLLECTION_ANNOTATION, query);
        return ls_versions;
    }
    
    /**
     * Get all version of annotations by ObjectID. 
     * @param annotation.objectID
     */
    public void getAllVersionsOfAnnotationByObjectID() throws IOException, ServletException, Exception {
        // TODO: This will go away because it can get crazy. We may build in some
        // helper services like getAllAncestors() for a path back to prime or
        // getAllDescendents() for a real mess (this method called on prime would be this as written)
        // @cubap @agree.  This was an original hanyan method, I only changed it to fit with how the rest of the methods work.
        Boolean approved = methodApproval(request, "get");
        if(approved){
            content = processRequestBody(request);
        }
        else{
            content = null;
        }
        if(null!=content){
            JSONObject received = JSONObject.fromObject(content);
            if(received.containsKey("objectID")){
                //find one version by objectID
                BasicDBObject query = new BasicDBObject();
                query.append("_id", new ObjectId(received.getString("objectID")));
                DBObject myAnno = mongoDBService.findOneByExample(Constant.COLLECTION_ANNOTATION, query);
                if(null != myAnno){
                    //find the versionID
                    BasicDBObject bMyAnno = (BasicDBObject) myAnno;
                    //find by versionID to get all versions
                    BasicDBObject queryOfAllVersion = new BasicDBObject();
                    queryOfAllVersion.append("originalAnnoID", bMyAnno.getString("originalAnnoID"));
                    List<DBObject> ls_versions = mongoDBService.findByExample(Constant.COLLECTION_ANNOTATION, queryOfAllVersion);
                    ls_versions.add(myAnno);//add the original annotation (because its orginalAnnoID is empty String)
                    JSONArray ja = JSONArray.fromObject(ls_versions);
                    try {
                        response.addHeader("Access-Control-Allow-Origin", "*");
                        response.setStatus(HttpServletResponse.SC_OK);
                        out = response.getWriter();
                        out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(ja));
                    } 
                    catch (IOException ex) {
                        Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                else{
                    response.addHeader("Access-Control-Allow-Origin", "*");
                    send_error("Object(s) not found.", HttpServletResponse.SC_NOT_FOUND);
                }
            }
            else{
                send_error("Provided object did not contain key 'objectID'.", HttpServletResponse.SC_BAD_REQUEST);
            }
        }
    }
    
    /**
     * Get annotation by objectiD.  Strip all unnecessary key:value pairs before returning.
     * @param objectID (oid)
     * @return annotation object
     */
    public void getAnnotationByObjectID() throws IOException, ServletException, Exception{
        Boolean approved = methodApproval(request, "get");
        if(approved){
            // TODO: @theHabes Does `approved` really have to be declared first.
            // It is only used for this check, so maybe just in one line reads easier.
            // Also, the outcome of this check is only setting `oid`, which is then
            // checked, which means these two conditions should be combined as well.
            
            // @cubap @answer. oid is set by the servlet context which is why I do not call processRequestBody(request) on this.  You do not
            // pass the oid as a parameter or in the body, the struts.xml and web.xml set up tells this method to get the ID off the end of the
            // URL.  By the time you get to this method, either oid is null or it isn't.  Regardless of whether or not you have the ID, 
            // this still needs to be approved as a get.  If you POST to this method and approved is false, making oid null will let the response of send_error() come out. If
            // it is approved as a GET, the fact the oid is invalid or null will return the 404.  They are separate fails with separate response codes, so they
            // must be handled separately to avoid error stacking.
        }
        else{
            oid=null;
        }
        if(null != oid){
            //find one version by objectID
            BasicDBObject query = new BasicDBObject();
            query.append("_id", new ObjectId(oid));
            DBObject myAnno = mongoDBService.findOneByExample(Constant.COLLECTION_ANNOTATION, query);
            if(null != myAnno){
                BasicDBObject bdbo = (BasicDBObject) myAnno;
                JSONObject jo = JSONObject.fromObject(myAnno.toMap());
                //The following are rerum properties that should be stripped.  They should be in __rerum.
                jo.remove("_id");
                jo.remove("addedTime");
                jo.remove("originalAnnoID");
                jo.remove("version");
                jo.remove("permission");
                jo.remove("forkFromID"); // retained for legacy v0 objects
                jo.remove("serverName");
                jo.remove("serverIP");
                try {
                    response.addHeader("Content-Type", "application/ld+json");
                    // @theHabes: ? Should we check that the object actually has @context?
                    // I'm not certain how to handle malformed JSON-LD
                   //@cubap @agree when we get to web annotation standard and IIIF compliance
                    response.addHeader("Access-Control-Allow-Origin", "*");
                    response.setStatus(HttpServletResponse.SC_OK);
                    out = response.getWriter();
                    out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(jo));
                } 
                catch (IOException ex) {
                    Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else{
                send_error("No object found with provided id '"+oid+"'.", HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }
    
    /**
     * Get annotations by given properties. 
     * @param Object with key:value pairs with conditions to match against.
     * @reutrn list of annotations that match the given conditions.
     */
    // @theHabes ? Why does getAnnotationByID() not just point into here with the ID?
    // Is it just to fire the findOneByExample()? Would it be better to do that here?
    
    // @cubap @answer. oid is set by the servlet context for getAnnoByID and does not process any request body.  It is handled as a special case where only
    // one object is returned.  This method must process a request body and returns an array, even an array of one.  
    // We never want getAnnoByID to return an array or feel the need to read a request body/parameters.
            
    public void getAnnotationByProperties() throws IOException, ServletException, Exception{
        Boolean approved = methodApproval(request, "get");
        if(approved){
            content = processRequestBody(request);
        }
        else{
            content = null;
        }
        // @theHabes: Just try-catch or false check JSONObject.fromObject(processRequestBody(request))
        // since this is the content= free version.
        if(null != content){
            JSONObject received = JSONObject.fromObject(content);
                // @theHabes ? Whaddya think about pulling out a method buildJSON() that
                // does this whole try-catch with a error for malformed stuff (or an 
                // actionable return) so we don't have to repeat and wrap all this each time.
                // @cubap @answer processRequestBody() is pretty much doing that for us.  Switching on it's response should handle that.
           
            BasicDBObject query = new BasicDBObject();
            Set<String> set_received = received.keySet();
            for(String key : set_received){
                query.append(key, received.get(key));
            }
            List<DBObject> ls_result = mongoDBService.findByExample(Constant.COLLECTION_ANNOTATION, query);
            JSONArray ja = new JSONArray();
            for(DBObject dbo : ls_result){
                ja.add((BasicDBObject) dbo);
            }
            if(ls_result.size() > 0){
                try {
                    response.addHeader("Content-Type","application/ld+json");
                    response.addHeader("Access-Control-Allow-Origin", "*");
                    response.setStatus(HttpServletResponse.SC_OK);
                    out = response.getWriter();
                    out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(ja));
                } 
                catch (IOException ex) {
                    Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else{
                send_error("Object(s) not found using provided properties '"+received+"'.", HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }
    
    /**
     * Save a new annotation. 
     * @param all annotation properties.
     */
    public void saveNewAnnotation() throws IOException, ServletException, Exception{
        Boolean approved = methodApproval(request, "create");
        if(approved){
            content = processRequestBody(request);
        }
        else{
            content = null;
        }
        // @theHabes: Just try-catch or false check JSONObject.fromObject(processRequestBody(request))
        // since this is the content= free version.
        // @cubap @agree.  I did not unwrap the original try{JSONPARSE}{catch{JSONPARSE error}, processRequestBody does that for us.
        // My secondary thought for leaving it was in case an error occurred in JSON.accumulate or JSON.element, but this is unnecessary.
        
        // @cubap @agree. We can let the falsey state of processRequestBody() be the switch instead of the null check.  Keep the issue of
        // error stacking in the back of your mind as we develop this and remember we can't throw Exceptions
       
        if(null != content){
            try{
                JSONObject received = JSONObject.fromObject(content);
                 
                DBObject dbo = (DBObject) JSON.parse(received.toString());
                String newObjectID = mongoDBService.save(Constant.COLLECTION_ANNOTATION, dbo);
                //set @id from _id and update the annotation
                BasicDBObject dboWithObjectID = new BasicDBObject((BasicDBObject)dbo);

                String uid = "http://devstore.rerum.io/rerumserver/id/" + dboWithObjectID.getObjectId("_id").toString();

                dboWithObjectID.append("@id", uid);
                mongoDBService.update(Constant.COLLECTION_ANNOTATION, dbo, dboWithObjectID);
                JSONObject jo = new JSONObject();
                
                jo.element("code", HttpServletResponse.SC_CREATED);
                jo.element("@id", uid);
                try {
                    response.addHeader("Access-Control-Allow-Origin", "*");
                    response.addHeader("Location", uid);
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    out = response.getWriter();
                    out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(jo));
                } 
                catch (IOException ex) {
                    Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            catch (Exception ex){ // try { parse JSON }
                send_error("Trouble parsing JSON", HttpServletResponse.SC_BAD_REQUEST);
            }
        }

    }
    
    public void setVal(){
        
    }
    
    public void unsetVal(){
        
    }
    
    /**
     * Update a given annotation. PUT that does not set or unset only.
     * This is one place new branches of an annotation may be created
     * when the `annotation.objectID` resolves to an object that has
     * an entry in .__rerum.history.next already.
     * @param annotation.objectID
     * @param all annotation properties include updated properties. 
     * @FIXME things are in __rerum now
     * @ignore the following keys (they will never be updated)
     *      @id
     *      objectID
     */
    public void updateAnnotation() throws IOException, ServletException, Exception{
        Boolean approved = methodApproval(request, "update");
        if(approved){
            content = processRequestBody(request);
        }
        else{
            content = null;
        }
        // @theHabes: Just try-catch or false check JSONObject.fromObject(processRequestBody(request))
        // since this is the content= free version.
        // @cubap @agree.  I did not unwrap the original try{JSONPARSE}{catch{JSONPARSE error}, processRequestBody does that for us.
        // My secondary thought for leaving it was in case an error occurred in JSON.accumulate or JSON.element, but this is unnecessary.
        
        // @cubap @agree. We can let the falsey state of processRequestBody() be the switch instead of the null check.  Keep the issue of
        // error stacking in the back of your mind as we develop this and remember we can't throw Exceptions
       
        if(null!= content){
            try{
                BasicDBObject query = new BasicDBObject();
                JSONObject received = JSONObject.fromObject(content); //object that has an id and new key:val pairs.
                query.append("@id", received.getString("@id").trim());
                BasicDBObject result = (BasicDBObject) mongoDBService.findOneByExample(Constant.COLLECTION_ANNOTATION, query); //The result DB object
                
                if(null != result){
                    Set<String> update_anno_keys = received.keySet();
                    JSONArray update_rerum_options = received.getJSONArray("__rerum");
                    boolean existingOptions = false; //Does the result DB object already contain __rerum
                    if(result.containsKey("__rerum")){
                        existingOptions = true;
                    }
                    //If the object already in the database contains the key found from the object recieved from the user, update it barring a few special keys
                    for(String key : update_anno_keys){
                        if(result.containsKey(key) && (!key.equals("@id") || !key.equals("__rerum")) || !key.equals("objectID")){
                            result.remove(key);
                            result.append(key, received.get(key));
                        }
                    }
                    JSONObject existing_object = JSONObject.fromObject(result); 
                    //If the object already in the database already contained __rerum, we can update that field
                    if(existingOptions){
                        existing_object = configureRerumOptions(existing_object);//The existing_object actually comes back as the new object to save here
                        //tricky because we can't use JSONObject here but needed one to configure __rerum on the result.
                        //convert JSONObject of configured result back to a BasicDBObject so we can write it back to mongo
                        //this could probably be optimized somehow, this seems too expensive for what it is trying to do.
                        result = (BasicDBObject) JSON.parse(existing_object.toString()); 
                    }
                    else{ //__rerum did not exist so we could not configure it (v0 
                        //Don't update any __rerum stuff because this key did not exist in the object already
                        //FIXME in the future we may still want to configureRerumOptions(existing_object) anyway.
                    }
                    mongoDBService.update(Constant.COLLECTION_ANNOTATION, query, result); //TODO @deprecate
                    // @cubap @theHabes #8 #22 FIXME TODO in the future, save the object with the configured __rerum as a new object
                    //Then we need to pass that new @id to a function that will update the history.next property of the originally received object as deomonstrated below.
                    //String newNextID = mongoDBService.save(existing_object, query, result);
                    //alterHistoryNext(received.getString("@id"),  newNextID);
                    JSONObject jo = new JSONObject();
                    jo.element("code", HttpServletResponse.SC_OK);
                    try {
                        response.addHeader("Access-Control-Allow-Origin", "*");
                        response.setStatus(HttpServletResponse.SC_OK);
                        out = response.getWriter();
                        out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(jo));
                    } 
                    catch (IOException ex) {
                        Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
                else{
                    send_error("Object(s) to update not found.", HttpServletResponse.SC_NOT_FOUND);
                }
            }
            catch(Exception ex){ //could not parse JSON
                send_error("Trouble parsing JSON", HttpServletResponse.SC_BAD_REQUEST);
            }
        }
    }
    
    /**
     * Save current annotation to a new version. 
     * @param objectID
     * @param any to be updated annotation properties. 
     * @FIXME things are in __rerum now
     */
    public void saveNewVersionOfAnnotation() throws IOException, ServletException, Exception{
        // TODO: This is all going to be redone for new versioning.
        // Simply, it will save a new object with .__rerum.history[next,previous,prime] set.
        Boolean approved = methodApproval(request, "create");
        if(approved){
            content = processRequestBody(request);
        }
        else{
            content = null;
        }
        // @theHabes: Just try-catch or false check JSONObject.fromObject(processRequestBody(request))
        // since this is the content= free version.
        // @cubap @agree.  I did not unwrap the original try{JSONPARSE}{catch{JSONPARSE error}, processRequestBody does that for us.
        // My secondary thought for leaving it was in case an error occurred in JSON.accumulate or JSON.element, but this is unnecessary.
        
        // @cubap @agree. We can let the falsey state of processRequestBody() be the switch instead of the null check.  Keep the issue of
        // error stacking in the back of your mind as we develop this and remember we can't throw Exceptions
       
        if(null!= content){
            try{
                BasicDBObject query = new BasicDBObject();
                JSONObject received = new JSONObject();
                received = JSONObject.fromObject(content);
                query.append("_id", received.getString("@id").trim());
                BasicDBObject result = (BasicDBObject) mongoDBService.findOneByExample(Constant.COLLECTION_ANNOTATION, query);
                if(null != result){
                    BasicDBObject versionQuery = new BasicDBObject();
                    versionQuery.append("originalAnnoID", result.get("originalAnnoID"));
                    BasicDBObject orderby = new BasicDBObject();
                    orderby.append("version", 1);
                    List<DBObject> ls_count = mongoDBService.findByExampleWithOrder(Constant.COLLECTION_ANNOTATION, versionQuery, orderby);
                    if(ls_count.size() >= 10){
                        //the upper limit of version number is 10, when the 11th comes in, it will delete the first one and put 11th as 10th. 
                        BasicDBObject first = (BasicDBObject) ls_count.get(0);
                        BasicDBObject last = (BasicDBObject) ls_count.get(ls_count.size() - 1);
                        //delete the 1st record.
                        mongoDBService.delete(Constant.COLLECTION_ANNOTATION, first);
                        int versionNum = last.getInt("version");
                        
                        received.remove("version");
                        received.accumulate("version", versionNum + 1);
                        Map<String, Object> values = received;
                        BasicDBObject dbo = new BasicDBObject(values);
                        String newObjectID = mongoDBService.save(Constant.COLLECTION_ANNOTATION, dbo);
                        //set @id to objectID and update the annotation
                        BasicDBObject dboWithObjectID = new BasicDBObject(dbo);
                        //used to be replace, not put.  Not sure why.
                        dboWithObjectID.put("@id", newObjectID);
                        mongoDBService.update(Constant.COLLECTION_ANNOTATION, dbo, dboWithObjectID);
                        JSONObject jo = new JSONObject();
                        jo.element("code", HttpServletResponse.SC_OK);
                        jo.element("newObjectID", newObjectID);
                        try {
                            response.setStatus(HttpServletResponse.SC_CREATED); //FIXME: Or should this be OK?
                            out = response.getWriter();
                            out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(jo));
                        } 
                        catch (IOException ex) {
                            Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    else{
                        BasicDBObject last = (BasicDBObject) ls_count.get(ls_count.size() - 1);
                        int versionNum = last.getInt("veresion");
                        received.remove("version");
                        received.accumulate("version", versionNum + 1);
                        Map<String, Object> values = received;
                        BasicDBObject dbo = new BasicDBObject(values);
                        String newObjectID = mongoDBService.save(Constant.COLLECTION_ANNOTATION, dbo);
                        //set @id to objectID and update the annotation
                        BasicDBObject dboWithObjectID = new BasicDBObject(dbo);
                        //used to be replace, not put.  Not sure why.
                        dboWithObjectID.put("@id", newObjectID);
                        mongoDBService.update(Constant.COLLECTION_ANNOTATION, dbo, dboWithObjectID);
                        JSONObject jo = new JSONObject();
                        jo.element("code", HttpServletResponse.SC_OK);
                        jo.element("newObjectID", newObjectID);
                        try {
                            response.setStatus(HttpServletResponse.SC_CREATED); //FIXME: or should this be OK?
                            out = response.getWriter();
                            out.write(mapper.writer().withDefaultPrettyPrinter().writeValueAsString(jo));
                        } 
                        catch (IOException ex) {
                            Logger.getLogger(AnnotationAction.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
                else{
                    send_error("The annotation you are trying to make a new version of does not exist.", HttpServletResponse.SC_NOT_FOUND);
                }
            }
            catch(Exception ex){ //could not parse JSON
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        }
    }
       
    /**
     * Delete a given annotation. 
     * @param annotation.@id
     */
    public void deleteAnnotation() throws IOException, ServletException, Exception{
        Boolean approved = methodApproval(request, "delete");
        if(approved){
            content = processRequestBody(request);
        }
        else{
            content = null;
        }
        // @theHabes: Just try-catch or false check JSONObject.fromObject(processRequestBody(request))
        // since this is the content= free version.
        // @cubap @agree.  I did not unwrap the original try{JSONPARSE}{catch{JSONPARSE error}, processRequestBody does that for us.
        // My secondary thought for leaving it was in case an error occurred in JSON.accumulate or JSON.element, but this is unnecessary.
        
        // @cubap @agree. We can let the falsey state of processRequestBody() be the switch instead of the null check.  Keep the issue of
        // error stacking in the back of your mind as we develop this and remember we can't throw Exceptions
       
        if(null != content){ 
            BasicDBObject query = new BasicDBObject();
            try{
                JSONObject received = JSONObject.fromObject(content);
                if(received.containsKey("@id")){
                    // TODO: also support jsut the URI in the body?
                    //@cubap @agree.  hanyan thought these methods would always be taking objects.
                    query.append("@id", received.getString("@id").trim());
                    mongoDBService.delete(Constant.COLLECTION_ANNOTATION, query);
                    response.setStatus(HttpServletResponse.SC_OK);
                }
                else{
                     send_error("annotation provided for delete has no @id, could not delete", HttpServletResponse.SC_BAD_REQUEST);
                }
            }
            catch (Exception ex){  // try {parse JSON}
                send_error("annotation provided for delete was not JSON, could not get id to delete", HttpServletResponse.SC_BAD_REQUEST);
            }
        }
    }
    
     /**
     * Validate data is IIIF compliant against IIIF's validator.  This object is intended for creation and not yet saved into RERUM so it does not yet have an @id.
     * The only way to hit the IIIF Validation API is to use the object's @id. The idea would be to save the objects to get an id, hit the 
     * IIIF API and if the object was intended to be IIIF, delete it from the store and return an error to the user.
     * 
     * In the case the object was not intended to be IIIF, do not return an error. Since the methods calling this will handle what happens based of iiif_return.okay,
     * just set okay to 1 and the methods calling this will treat it as if it isn't a problem. 
     * 
     * @param objectToCheck A JSON object to parse through and validate.  This object has not yet been saved into mongo, so it does not have an @id yet
     * @param intendedIIIF A flag letting me know whether or not this object is intended to be IIIF.  If it isn't, don't treat validation failure as an error.
     * @return iiif_return The return JSONObject from hitting the IIIF Validation API.
     */
    public JSONObject checkIIIFCompliance(JSONObject objectToCheck, boolean intendedIIIF) throws MalformedURLException, IOException{
        JSONObject iiif_return = new JSONObject();
        DBObject dbo = (DBObject) JSON.parse(objectToCheck.toString());
        String newObjectID = mongoDBService.save(Constant.COLLECTION_ANNOTATION, dbo);
        iiif_return = checkIIIFCompliance(newObjectID, "2.1"); //If it is an object we are creating, this line means @context must point to Presentation API 2 or 2.1
        if(iiif_return.getInt("okay") == 0){
            if(intendedIIIF){
                //If it was intended to be a IIIF object, then remove this object from the store because it was not valid and return an error to the user
                BasicDBObject query = new BasicDBObject();
                query.append("_id", newObjectID);
                mongoDBService.delete(Constant.COLLECTION_ANNOTATION, query);
            }
            else{
                //Otherwise say it is ok so the action looking do validate does not send_error()
                iiif_return.element("okay", 1);
            }
        }
        return iiif_return;
    }
    
     /**
     * A JSONObject that already has an @id can be validated against this IIIF validation URL.  It will return a JSONObject.
     * A save or update action could hit this to see if the resulting object is IIIF valid.  If not, a 'rollback' or 'delete' could be performed
     * and the warnings and errors sent back to the user.  If using this function, it is assumed objURL is intended to be a IIIF URL.
     * 
     * @param objURL The @id or id URL of a IIIF JSON object represented as a String.
     * @param version The Intended Presentation API version to validate against represented as a String.  (1, 2 or 2.1)
     * @return iiif_return The return JSONObject from hitting the IIIF Validation API.
     */
    public JSONObject checkIIIFCompliance(String objURL, String version) throws MalformedURLException, IOException{
        JSONObject iiif_return = new JSONObject();
        String iiif_validation_url = "http://iiif.io/api/presentation/validator/service/validate?format=json&version="+version+"&url="+objURL;
        URL validator = new URL(iiif_validation_url);
        BufferedReader reader = null;
        StringBuilder stringBuilder;
        HttpURLConnection connection = (HttpURLConnection) validator.openConnection();
        connection.setRequestMethod("GET"); //hmm... I think this is right
        connection.setReadTimeout(15*1000);
        connection.connect();
        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        stringBuilder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null)
        {
          stringBuilder.append(line + "\n");
        }
        connection.disconnect();
        iiif_return = JSONObject.fromObject(stringBuilder.toString());
        return iiif_return;
    }

    @Override
    public void setServletRequest(HttpServletRequest hsr) {
        this.request = hsr;
    }

    @Override
    public void setServletResponse(HttpServletResponse hsr) {
        this.response = hsr;
    }

    /**
     * @return the mongoDBService
     */
    public MongoDBService getMongoDBService() {
        return mongoDBService;
    }

    /**
     * @param mongoDBService the mongoDBService to set
     */
    public void setMongoDBService(MongoDBService mongoDBService) {
        this.mongoDBService = mongoDBService;
    }

    /**
     * @return the acceptedServer
     */
    public AcceptedServer getAcceptedServer() {
        return acceptedServer;
    }

    /**
     * @param acceptedServer the acceptedServer to set
     */
    public void setAcceptedServer(AcceptedServer acceptedServer) {
        this.acceptedServer = acceptedServer;
    }

    /**
     * @return the content
     */
    public String getContent() {
        return content;
    }

    /**
     * @param content the content to set
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * @return the oid
     */
    public String getOid() {
        return oid;
    }

    /**
     * @param oid the oid to set
     */
    public void setOid(String oid) {
        this.oid = oid;
    }

}

