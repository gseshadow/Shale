package com.shale.desktop.net;

public final class LiveBusSmokeTest {
	public static void main(String[] args) throws Exception {
		int shaleClientId = 7; // pick any real tenant ID you use
		int userId = 1; // your test user

		String negotiateUrl = System.getenv("NEGOTIATE_ENDPOINT_URL");
		if (negotiateUrl == null || negotiateUrl.isBlank()) {
			throw new IllegalStateException("Set NEGOTIATE_ENDPOINT_URL first.");
		}

		// 1) Fetch wss:// URL from your Azure Function
		NegotiateClient negotiate = new NegotiateClient(negotiateUrl);
		String wss = negotiate.negotiateForTenant(shaleClientId, userId);
		System.out.println("Negotiated URL: " + wss);

		// 2) Connect the socket (no groups yet)
		LiveBusClient client = new LiveBusClient(new LiveBusClient.Handler() {
			@Override
			public void onOpen() {
				System.out.println("[livebus] OPEN");
			}

			@Override
			public void onMessage(String text) {
				System.out.println("[livebus] MSG " + text);
			}

			@Override
			public void onClosed(int code, String reason) {
				System.out.println("[livebus] CLOSED " + code + " " + reason);
			}

			@Override
			public void onError(Throwable error) {
				error.printStackTrace();
			}
		});

		client.connect(wss).join();
		client.connect(wss).join();

		// Join a tenant-scoped group so instances can “see” each other
		String group = "tenant:" + shaleClientId;
		client.joinGroup(group, 1).join();
		System.out.println("Joined group: " + group);

		// send a test event to everyone in the group
		client.sendToGroup(group,
				"{\"event\":\"Ping\",\"fromUser\":" + userId + "}", 2).join();
		System.out.println("Sent Ping");

		Thread.sleep(60_000);

		System.out.println("Exiting smoke test.");
	}
}
