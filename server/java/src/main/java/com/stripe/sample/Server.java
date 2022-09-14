package com.stripe.sample;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFiles;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.stripe.Stripe;
import com.stripe.exception.*;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Paths;
import java.util.HashMap;

public class Server {

  private static Gson gson = new Gson();

  static class ConfigResponse {

    private String publishableKey;

    public ConfigResponse(String publishableKey) {
      this.publishableKey = publishableKey;
    }
  }

  static class FailureResponse {

    private HashMap<String, String> error;

    public FailureResponse(String message) {
      this.error = new HashMap<String, String>();
      this.error.put("message", message);
    }
  }

  public static void main(String[] args) {
    port(4242);
    Dotenv dotenv = Dotenv.load();

    Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");

    // For sample support and debugging, not required for production:
    Stripe.setAppInfo(
      "stripe-samples/<your-sample-name>",
      "0.0.1",
      "https://github.com/stripe-samples"
    );

    staticFiles.externalLocation(
      Paths
        .get(
          Paths.get("").toAbsolutePath().toString(),
          dotenv.get("STATIC_DIR")
        )
        .normalize()
        .toString()
    );

    get(
      "/config",
      (request, response) -> {
        response.type("application/json");

        return gson.toJson(
          new ConfigResponse(dotenv.get("STRIPE_PUBLISHABLE_KEY"))
        );
      }
    );

    post(
      "/webhook",
      (request, response) -> {
        String payload = request.body();
        String sigHeader = request.headers("Stripe-Signature");
        String endpointSecret = dotenv.get("STRIPE_WEBHOOK_SECRET");

        Event event = null;

        try {
          event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
          // Invalid signature
          response.status(400);
          return "";
        }

        switch (event.getType()) {
          case "payment_intent.succeeded":
            // Fulfill any orders, e-mail receipts, etc
            // To cancel the payment you will need to issue a Refund
            // (https://stripe.com/docs/api/refunds)
            System.out.println("💰Payment received!");
            break;
          case "payment_intent.payment_failed":
            System.out.println("❌ Payment failed.");
            break;
          default:
            // Unexpected event type
            response.status(400);
            return "";
        }

        response.status(200);
        return "";
      }
    );
  }
}
