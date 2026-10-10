# FXCalc

FXCalc converts one amount into several currencies at once. Choose a starting currency, add or remove destination currencies, and tap the swap icon on a result to make it the starting currency. The app remembers your currency choices.

Hold a currency card or its drag handle to reorder it. Drag near the top or bottom of the list to scroll. The order is saved automatically. TalkBack users can use the card’s move up and move down actions.

Choose a rate source with the settings button in the top bar. Changes are saved immediately; the dialog stays open to show the selected source's rate date, last download, and update status. Tap Done to close it:

- **ExchangeRate-API** - daily rates from open.er-api.com, quoted per US dollar.
- **Frankfurter** - official daily rates requested with USD as the base, with no API key required.
- **FloatRates** - USD-based rates refreshed every 12 hours, with no API key required.
- **Currency API (community)** - daily USD-based rates served through public CDNs, with no API key required and an automatic fallback host.
- **European Central Bank (ECB)** - the daily euro reference feed at ecb.europa.eu, normalized to USD by the app.

USD is the app's default starting currency and the internal reference for all rate tables. These are reference snapshots rather than live trading rates, and update schedules vary by source. The date shown in the app is the date of the published rates. Each source's last successful download is saved separately for offline use; the first download of a source needs an internet connection. Rates are indicative and may differ from the rate charged by a bank or card provider.

Build a debug APK with:

```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug
```
