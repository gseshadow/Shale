# Live Update Architecture

Purpose:
Real-time updates across all Shale clients.

Technology:
Azure Web PubSub

Hub:
shale

Connection flow:

Desktop
-> Azure Function negotiate endpoint
-> Receive websocket URL
-> Connect to Azure Web PubSub
-> Join tenant:{ShaleClientId}

Examples:

tenant:7

Client components:

- NegotiateClient
- LiveBusClient
- LiveEventDispatcher

Rules:

- Updates are tenant scoped.
- Events should be broadcast to tenant groups.
- Never broadcast data across tenant boundaries.
- UI should refresh through dispatcher events rather than direct controller coupling.