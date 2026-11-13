/* Include after private inputs in the same batch. No mutation; pin this packet's exact tuple. */
IF CONVERT(varbinary(max),ISNULL(@BindDatabase,N''))<>CONVERT(varbinary(max),N'ShaleMcpAcceptance')
 OR CONVERT(varbinary(max),ISNULL(@BindOrigin,N''))<>CONVERT(varbinary(max),N'https://shale-mcp-acceptance.azurewebsites.net')
 OR CONVERT(varbinary(max),ISNULL(@BindIssuer,N''))<>CONVERT(varbinary(max),N'https://shale-mcp-acceptance.azurewebsites.net/oauth/mcp')
 OR CONVERT(varbinary(max),ISNULL(@BindResource,N''))<>CONVERT(varbinary(max),N'https://shale-mcp-acceptance.azurewebsites.net/mcp')
 THROW 59809,'STOP: exact acceptance database and issuer/resource origin required.',1;
/* The unchanged binding session/validate/install/postcheck still enforce actual server,
   OFF/evidence/approval gates, 28 samples, source receipt and conflicting-row refusal. */
