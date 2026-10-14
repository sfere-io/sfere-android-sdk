# Sfere Analytics for Android

Collects first-party fan events in your Android app and sends them to Sfere. The payload and the event names are the same as Sfere's browser and server SDKs.

- Artifact: `io.sfere:analytics`
- Android 5.0+ (API 21)
- Kotlin and Java

## Install

> **Not yet published.** No release exists yet: the repository has no remote and no tag, and Maven publishing is not configured yet, so the steps below describe the planned install path for the beta.

During the beta the SDK is published privately to GitHub Packages. Add the repository and the dependency:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.github.com/fanfinity/sfere-mobile-sdks") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.token").orNull // a token with read:packages
            }
        }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("io.sfere:analytics:0.1.0")
}
```

## Set up

Call `setup` once, in `Application.onCreate`:

```kotlin
class FanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sfere.setup(this, SfereConfig(
            writeKey = "keyId:secret",            // your browser key; never a server key
            host = "https://ingest.example.com",  // your ingestion origin, no path
        ))
    }
}
```

Then use `Sfere.shared` anywhere. Calls made before `setup`, or after a failed setup, are ignored. The SDK never throws into your app. If the config is invalid, setup logs the reason under the `Sfere` tag and the SDK does nothing.

| Option | Default | |
|---|---|---|
| `writeKey`, `host` | required | `host` must be `https://`. `http://` is allowed only in debuggable builds or with `debug = true`. |
| `flushAt` | 20 | Events per upload (max 100). |
| `flushInterval` | 30 | Seconds an event may wait before upload. |
| `sessionTimeoutMinutes` | 30 | Inactivity before a new session starts. |
| `trackLifecycleEvents` | true | `app_installed`, `app_updated`, `app_opened`, `app_backgrounded`. |
| `trackDeepLinks` | true | `deep_link_opened` when a link opens an Activity. |
| `consent` | unset | Consent categories, e.g. `mapOf("analytics" to true, "advertising" to false)`. It is the initial value only: consent already stored by `setConsent` wins. |
| `debug` | false | Logs payloads and rejected calls. |

Java:

```java
Sfere.setup(this, new SfereConfig("keyId:secret", "https://ingest.example.com"));
Sfere.getShared().track("add_to_cart", properties);
```

## Events

Use the GA4 event names and properties listed in Sfere's events reference (snake_case):

```kotlin
val sfere = Sfere.shared
sfere.identify("user_123", mapOf("email" to "fan@example.com", "phone" to "+966500000000"))
sfere.screen("Match Center", mapOf("match_id" to "42"))
sfere.track("add_to_cart", mapOf(
    "currency" to "SAR",
    "value" to 89.99,
    "items" to listOf(mapOf("item_id" to "sku_12345", "item_name" to "Away Jersey", "price" to 89.99, "quantity" to 1)),
))
sfere.track("match_ticket_scanned", mapOf("match_id" to "42"))   // your own events: snake_case too
sfere.reset()                                                      // on logout
```

Send email and phone as plain values. Sfere hashes them for each destination, so never hash them yourself.

### Purchases

```kotlin
sfere.purchase(SferePurchase(
    transactionId = "order_98765",
    currency = "SAR",
    value = 256.96,
    tax = 16.99,
    shipping = 10.0,
    dedupId = "order_98765",   // set this when your server also sends the purchase
    items = listOf(SfereItem(itemId = "sku_12345", itemName = "Away Jersey", price = 89.99, quantity = 2)),
))
```

### Sent automatically

| Event | When |
|---|---|
| `app_installed` | First launch after install |
| `app_updated` | First launch after an update |
| `app_opened` | Cold start and each return to the foreground (`from_background`) |
| `app_backgrounded` | The app goes to the background (queued events are uploaded) |
| `session_started` | The first event of each session |
| `deep_link_opened` | A link opens the app (`url`, `referring_application`) |

Every event also carries:
- `context.session.id`
- `context.library`
- `context.app`, `device`, `os`, `screen` and `network`
- `context.consent`, when it is set
- `context.traits` (the identify traits), on events other than identify
- the link's click IDs and UTMs in `context.clientIds` and `context.campaign`, for the rest of the session

Every `track` event also carries `properties.sfere_dedup_id`.

## Consent

```kotlin
Sfere.shared.setConsent(mapOf("analytics" to true, "advertising" to true))
```

The consent is sent on every later event and remembered across launches. `setConsent(null)` sets it back to unknown, and the field is then left out.

## Deep links

Links that start an Activity are recorded automatically. Add an intent filter as usual:

```xml
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="app.example.com" />
    </intent-filter>
</activity>
```

When an Activity that is already running receives a link, record it yourself:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    Sfere.shared.openUrl(intent, referrer = referrer?.toString())
}
```

Don't also call `openUrl` from `onCreate`, or the link is recorded twice. If a link SDK hands you the URL in a callback, use `openUrl(uri)` or `openUrl("https://…")`.

Click IDs come from the link (`gclid`, `wbraid`, `gbraid`, `ttclid`, `ScCid`, `twclid`, `epik`, `msclkid`, `li_fat_id`, `oppref`, `rdt_cid`). `fbclid` becomes `fbc`. UTMs and `gad_source` / `gad_campaignid` go to `context.campaign`.

## Opt-out

```kotlin
Sfere.shared.setEnabled(false)   // stops collection and uploads; remembered across launches
```

## Google Play Data safety

The SDK collects:
- app interactions (events and screens)
- the identifiers you send (user ID, email, phone)
- device and OS details
- ad click IDs from links

It does not collect the Android Advertising ID. Data goes only to the host you configure, over HTTPS. Declare the data types in your Data safety form according to the events you send.

## Testing against a local mock

Point `host` at a mock ingestion server on your computer:
- On the emulator, `10.0.2.2` is your computer: `host = "http://10.0.2.2:4311"`.
- On a physical device, use your computer's LAN IP, and bind the mock to `0.0.0.0`.

`http://` hosts work only in debuggable builds. Android also blocks cleartext traffic by default, so allow it for debug builds only:

```xml
<!-- app/src/debug/res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

```xml
<!-- app/src/debug/AndroidManifest.xml -->
<application android:networkSecurityConfig="@xml/network_security_config" />
```

Set `debug = true` to log every payload under the `Sfere` tag.

## Developing this SDK

```bash
cd android
./gradlew :sfere-analytics:testDebugUnitTest   # unit + transport tests (JVM, no emulator)
./gradlew :sfere-analytics:assembleRelease     # builds the AAR
./gradlew --stop                               # stop the Gradle daemons afterwards
```

The tests read the shared fixtures in `../fixtures` (deep-link click IDs, event renames), which every Sfere SDK must pass.
