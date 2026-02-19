# Azure Function Example: `/api/live/case-updated`

```java
package com.shale.functions.live;

import com.azure.messaging.webpubsub.WebPubSubServiceClient;
import com.azure.messaging.webpubsub.WebPubSubServiceClientBuilder;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.util.Optional;

public class LiveCaseUpdatedFunction {

    private static final String HUB_NAME = System.getenv().getOrDefault("WEBPUBSUB_HUB", "live");
    private static final String CONNECTION_STRING = System.getenv("WEBPUBSUB_CONNECTION_STRING");

    private final WebPubSubServiceClient serviceClient = new WebPubSubServiceClientBuilder()
            .connectionString(CONNECTION_STRING)
            .hub(HUB_NAME)
            .buildClient();

    @FunctionName("LiveCaseUpdated")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "live/case-updated")
            HttpRequestMessage<Optional<CaseUpdatedRequest>> request,
            final ExecutionContext context) {

        CaseUpdatedRequest body = request.getBody().orElse(null);
        if (body == null || body.caseId == null || body.shaleClientId == null || body.updatedByUserId == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Invalid payload")
                    .build();
        }

        String payload = "{\"event\":\"CaseUpdated\",\"caseId\":" + body.caseId
                + ",\"shaleClientId\":" + body.shaleClientId
                + ",\"updatedByUserId\":" + body.updatedByUserId + "}";

        String groupName = "client-" + body.shaleClientId;
        serviceClient.sendToGroup(groupName, payload);

        context.getLogger().info("Published CaseUpdated to group " + groupName);

        return request.createResponseBuilder(HttpStatus.OK)
                .body("ok")
                .build();
    }

    public static final class CaseUpdatedRequest {
        public Integer caseId;
        public Integer shaleClientId;
        public Integer updatedByUserId;
    }
}
```

Environment variables used by the function:
- `WEBPUBSUB_CONNECTION_STRING`
- `WEBPUBSUB_HUB` (optional, defaults to `live`)
