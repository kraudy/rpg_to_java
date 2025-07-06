import com.ibm.as400.access.*;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class JacksonJsonParser {
  public static void main(String... args){

    JsonFactory factory = new JsonFactory();
    JsonParser parser = factory.createParser(fis);
    while (parser.nextToken() != JsonToken.END_ARRAY) {
        if (parser.getCurrentName() != null && parser.getCurrentName().equals("employees")) {
            parser.nextToken(); // Move to array start
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                // Process each employee object
                JSONObject employee = new JSONObject(parser.readValueAsTree().toString());
                // Write to PDF, log, etc.
            }
        }
    }
    parser.close();
    
  }
}
