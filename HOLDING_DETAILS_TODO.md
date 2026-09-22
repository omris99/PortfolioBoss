# Holding Details — Session Implementation Plan

> **למי שקורא זאת בסשן חדש:** מדריך מימוש שלב-אחר-שלב לפיצ'ר "פרטי החזקה": לכל holding בפורטפוליו
> יופיעו **סקטור, תאריך קנייה, תאריך מכירה וזמן החזקה כולל**, ומשתמש יוכל להזין ב-UI סקטור ועסקאות
> (קנייה / מכירה חלקית) ידנית. הכול נשמר ב-DB, וה-DB מתעדכן מ-TWS בכל חיבור.
> כל פריט כולל: הקוד הקיים הרלוונטי, מה לשנות, ולמה — כך שאפשר לממש בלי לקרוא את כל הקבצים מחדש.
>
> זהו למעשה **Milestone 1 של [TODO.md](TODO.md)** (Maven + Postgres + Spring Boot REST), מוקדם יותר, עם
> טבלת `trade` חדשה שלא מופיעה שם ועם טפסי הזנה ב-UI (חלק מ-Milestone 2).
>
> סיכון לכל פריט: 🟢 נמוך | 🟡 בינוני | 🔴 גבוה. **✅ ליד מספר הפריט = בוצע.**
> **לפני כל סשן: לוודא ש-`./run.sh` בונה ורץ (מול TWS מחובר; ללא TWS — ההתנהגות זהה למתועד).**
> **בסוף כל סשן: לבצע את "בדיקת האימות" של אותו סשן לפני מעבר לסשן הבא.**

---

## החלטות שהתקבלו (לא לפתוח מחדש)

1. **DB: PostgreSQL מקומי** (כמו ב-TODO.md; חלק ממסלול הלמידה Maven → JUnit → Spring → Postgres).
   חלופת קובץ-בודד (H2/SQLite) נדחתה כברירת מחדל; אם ההתקנה מפריעה — משנה רק את סשן 1.
2. **הזנה ישר דרך טפסים ב-UI** (לא דרך כלי DB) → יש endpoints כותבים (סשן 4) ולכן Spring Boot כבר עכשיו
   (אין לנו היום פענוח JSON; `PortfolioJson` רק כותב).
3. **תאריך קנייה, תאריך מכירה וזמן החזקה *נגזרים*** מטבלת `trade` שמוזנת ידנית — הם לא נשמרים כעמודות.
   כך אין הזנה כפולה ואין סטייה. **Flex Query נדחה** (נשאר אופציה עתידית כמקור חלופי לעסקאות).
4. **IB נשאר מקור האמת לכמות ולעלות ממוצעת.** הנתונים הידניים מוסיפים רק את ה"מתי" וה"למה".
5. **סקטור מוזן ידנית** (טקסט חופשי). אין `reqContractDetails`.
6. **החזקה שנעלמה מ-TWS לא נמחקת** — היא מסומנת `CLOSED`, כדי שהסקטור והעסקאות שהוזנו ביד לא יאבדו.
7. **Stack:** Java 21, **Spring Boot 4.1.1** (הגרסה ש-start.spring.io החזיר ב-19.09.2026), Spring Data JPA,
   Flyway (מיגרציות SQL מגורסות), PostgreSQL 17, JUnit 5.

## עקרונות קבועים לכל הסשנים

- **Read-only מול IB:** אין `placeOrder` / `cancelOrder` / callbacks של הוראות. הכתיבה החדשה היחידה היא
  ל-DB המקומי של PortfolioBoss.
- **ה-API נקשר ל-loopback בלבד** (`server.address=127.0.0.1`), **בלי הגדרת CORS**, ו-endpoints כותבים
  מקבלים **JSON בלבד** (ראה 4.4).
- **`GET /api/portfolio` — הצורה יציבה:** שדות קיימים לא משתנים ולא נמחקים; רק מוסיפים.
- **קונסול:** `[ib]`, `[api]`, `[ui]` קיימים; חדש: **`[db]`** (סנכרון) ו-`[db error]`.
- **קוד קריא (CLAUDE.md):** שמות תיאוריים, בלי משתנים של אות אחת, פונקציות/קומפוננטות קטנות.
- **לפני עריכת קובץ קיים — להסביר למשתמש מה ולמה** (העדפה מפורשת); לא לסמוך על "אושר כתוכנית".
- **Git / changelog / "עדכונים של סוף סשן"** (CLAUDE.md, README, reference_ui.md) — **רק ביוזמת המשתמש**
  (`/commit`, `/changelog`). לא לבצע אוטומטית בסוף סשן.

## מצב התחלתי (נבדק ב-19.09.2026)

- **לא מותקנים:** Maven, PostgreSQL (`psql` / `pg_dump`). **מותקנים:** Homebrew, Node 20.19.4, Docker
  (ה-daemon כבוי — לא בשימוש בתוכנית).
- `~/DevTools/twsapi/TwsApi.jar` קיים; לידו `~/DevTools/twsapi/jars/protobuf-java-4.29.3.jar` ו-`pom.xml`
  (`com.interactivebrokers:tws-api`, תלות `protobuf-java:4.29.3`). התיקייה `~/DevTools/google-proto-buf/`
  ש-`run.sh` מחפש (שורה 27) לא קיימת/ריקה — ב-Maven היא מוחלפת בתלות רגילה.
- `Contract.conid()` קיים ב-jar (נבדק) — זה המפתח היציב שנשמור לכל החזקה.

## ארכיטקטורת יעד

```
TWS ──> IbGateway ──> PortfolioSnapshot ──> PortfolioSyncService ──> Postgres
        (ללא שינוי)    (Holding + conId)      (upsert; CLOSED)        holding · trade · account_state
                                                                              │
UI (React) <──JSON── PortfolioController <── PortfolioQueryService ───────────┘   ← ה-API קורא *תמיד* מה-DB
   │                       (+ HoldingHistory: תאריכים וזמן החזקה נגזרים)
   └─ PUT / POST / DELETE ──> HoldingWriteController ──> TradeService ──> Postgres   (כותבים רק ל-DB)
```

```
src/main/java/portfolioboss/
├── Main.java                # @SpringBootApplication בלבד
├── TwsPortfolioRunner.java  # ApplicationRunner (TWS → sync → UI). הבעלים של host/port/clientId
├── ib/  model/  ui/         # IbGateway, PortfolioWrapper, Holding(+conId), PortfolioSnapshot, UiLauncher — תפקיד זהה
├── db/                      # *Entity, *Repository, PortfolioSyncService            (סשנים 1–2)
├── domain/                  # HoldingHistory — חישובים טהורים, בלי Spring          (סשן 3)
└── api/                     # PortfolioController, HoldingWriteController, *Request, ApiErrors   (סשנים 0, 2–4)
    └── response/            # *Response — מה שה-UI מקבל                                           (סשנים 0, 2–3)
src/main/resources/          # application.properties, db/migration/V1__portfolio_schema.sql
```

> **ההבחנה בשמות:** `model.Holding` (record) נשאר *קריאת IB* — מה ש-`updatePortfolio` החזיר.
> `HoldingEntity` הוא השורה ב-DB. `HoldingResponse` הוא מה שה-UI מקבל. שלושה דברים שונים בכוונה.

---

## סשן 0 — Maven + Spring Boot (בלי שינוי בהתנהגות)

> **תנאי מוקדם:** אין. **המטרה:** אותה התנהגות ואותו JSON כמו היום, אבל נבנה ב-Maven ורץ כאפליקציית
> Spring Boot. **עדיין אין DB.** מומלץ לעבוד על branch ייעודי — זה משנה את שיטת הבנייה כולה (המשתמש מחליט).
>
> **סטטוס (21.09.2026):** 0.1–0.6 ✅ בוצעו על branch `maven-spring-boot` (commit 8427918). הבדיקה החיה מול TWS דלוק ✅ — המשתמש הריץ ואישר שהכול עובד.

### ✅ 0.1 🟡 `brew install maven` + `pom.xml`

**קוד קיים רלוונטי:**
```bash
# run.sh שורות 31-34 — קומפילציה ידנית
mkdir -p "${OUT}"
find "${HERE}/src/main/java" -name '*.java' > "${OUT}/sources.txt"
javac -d "${OUT}" -cp "${CP}" @"${OUT}/sources.txt"
```
README: "Milestone 0 builds with plain `javac` via `run.sh`". מבנה התיקיות כבר Maven-סטנדרטי — **אין להזיז קבצים**.

**המימוש:** `brew install maven` (3.9.x), ואז `pom.xml` בשורש. הוא נגזר מ-Initializr (נבדק — שמות ה-artifacts
של Spring Boot 4 שונים ממדריכים ישנים):
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
    <relativePath/>
</parent>
<groupId>portfolioboss</groupId>
<artifactId>portfolioboss</artifactId>
<version>0.0.1-SNAPSHOT</version>
<properties><java.version>21</java.version></properties>

<dependencies>
    <dependency>  <!-- לא spring-boot-starter-web -->
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- IB: ה-jar אינו ב-Maven Central; מותקן מקומית (0.2). ה-version הוא רק תווית -->
    <dependency>
        <groupId>com.interactivebrokers</groupId>
        <artifactId>tws-api</artifactId>
        <version>local</version>
    </dependency>
    <dependency>  <!-- זהה לגרסה ב-pom של ה-TWS API -->
        <groupId>com.google.protobuf</groupId>
        <artifactId>protobuf-java</artifactId>
        <version>4.29.3</version>
    </dependency>
</dependencies>
<build><plugins>
    <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
</plugins></build>
```

**⚠️ בעת הוספת תלויות נוספות (סשן 1):** ליצור את ה-pom מחדש דרך start.spring.io ולהעתיק משם את השמות המדויקים
(`https://start.spring.io/pom.xml?type=maven-project&javaVersion=21&dependencies=web,data-jpa,postgresql,flyway,validation`),
לא לנחש.

---

### ✅ 0.2 🟢 התקנת ה-jar של IB ל-repo המקומי של Maven

**הבעיה:** `TwsApi.jar` אינו ב-Maven Central (CLAUDE.md: "External dependency not in the repo").

**המימוש — חד-פעמי, ומוטמע ב-`run.sh` (רק אם חסר):**
```bash
TWSAPI_M2="${HOME}/.m2/repository/com/interactivebrokers/tws-api/local"
if [[ ! -d "${TWSAPI_M2}" ]]; then
  mvn -q install:install-file -Dfile="${TWSAPI_JAR}" \
      -DgroupId=com.interactivebrokers -DartifactId=tws-api -Dversion=local -Dpackaging=jar
fi
```
**⚠️** אחרי החלפת ה-jar בגרסה חדשה של TWS API — להריץ את פקודת ה-install ידנית (הבדיקה למעלה בודקת רק קיום).

---

### ✅ 0.3 🔴 `Main` → נקודת כניסה של Spring Boot + `ApplicationRunner`

> **בפועל:** ה-runner הוא `TwsPortfolioRunner` — `@Component` נפרד, לא `@Bean` על `Main`. ה-test slices של Spring (`@WebMvcTest`) טוענים כל מה שמוצהר על ה-main class ומריצים כל `ApplicationRunner`, ו-runner שם התחבר ל-TWS האמיתי מכל בדיקה. בהמשך התוכנית (למשל 2.3) "ה-runner ב-`Main`" הוא `TwsPortfolioRunner`.

**קוד קיים רלוונטי:**
```java
// Main.java שורות 36-75 — הזרימה היום
public static void main(String[] args) throws InterruptedException {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
    int clientId = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_CLIENT_ID;
    ...
    PortfolioSnapshot snapshot = null;
    IbGateway gateway = new IbGateway();
    try { ...connect / awaitPortfolio / snapshot = gateway.snapshot()... }
    catch (IllegalStateException e) { ... } finally { gateway.disconnect(); }   // שורות 42-59

    if (snapshot == null) { System.exit(0); }                                    // שורות 61-64
    new ApiServer(API_PORT, snapshot).start();                                   // שורות 66-71
    new UiLauncher(Path.of("ui"), UI_PORT).launch();                             // שורה 74
}
```

**הבעיה:** צריך להחליף את `HttpServer` של ה-JDK ב-Spring, ולשמור: ארגומנטים פוזיציוניים (`./run.sh 7497 102`),
`Main` כבעלים של host/port/clientId (CLAUDE.md), "אין snapshot → יציאה", ו-`UiLauncher` אחרי שה-API למעלה.

**המימוש:**
```java
@SpringBootApplication(proxyBeanMethods = false)
public class Main {

    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7496;
    private static final int DEFAULT_CLIENT_ID = 101;
    private static final int TIMEOUT_SECONDS = 15;
    private static final int UI_PORT = 5174;            // חייב להתאים ל-ui/vite.config.ts

    public static void main(String[] args) {
        System.out.println(AppMetadata.getSignature() + " · read-only portfolio reader");
        SpringApplication.run(Main.class, args);
    }

    /** רץ פעם אחת אחרי שה-web server עלה: קורא את TWS, ואז פותח את ה-UI. */
    @Bean
    ApplicationRunner readPortfolioFromTws(SnapshotStore snapshotStore, ConfigurableApplicationContext context) {
        return arguments -> {
            List<String> positional = arguments.getNonOptionArgs();     // "7497 102" מ-run.sh
            int port = positional.size() > 0 ? Integer.parseInt(positional.get(0)) : DEFAULT_PORT;
            int clientId = positional.size() > 1 ? Integer.parseInt(positional.get(1)) : DEFAULT_CLIENT_ID;

            PortfolioSnapshot snapshot = readSnapshotFromTws(port, clientId);
            if (snapshot == null) {
                System.exit(SpringApplication.exit(context));           // כמו היום: אין מה להציג
            }
            snapshotStore.set(snapshot);
            new UiLauncher(Path.of("ui"), UI_PORT).launch();
        };
    }

    // שורות 42-59 הקיימות, מועברות כמות שהן: connect / await / snapshot / disconnect
    private static PortfolioSnapshot readSnapshotFromTws(int port, int clientId) throws InterruptedException { ... }
}
```

**⚠️ `proxyBeanMethods = false`:** `Main` היום `final` עם constructor פרטי (שורות 77-78). ללא הדגל Spring לא יכול לייצר לו
proxy; עם הדגל זה עובד. אם עדיין יש תלונה — להסיר את `final` ואת ה-constructor הפרטי.
**⚠️** ה-web server כבר מאזין בזמן שקוראים את TWS (עד 15 שניות) → בקשה שמגיעה לפני שיש snapshot חייבת לקבל 503 (0.4), לא NPE.
**⚠️** `API_PORT` נמחק; הפורט עובר ל-`application.properties`: `server.port=8080`, `server.address=127.0.0.1`
(אותו פורט — `vite.config.ts` לא משתנה). להוסיף `spring.main.banner-mode=off` כדי שהפלט לא יסתבך עם שורות ה-`[ib]`.

---

### ✅ 0.4 🟡 `ApiServer` + `PortfolioJson` → `PortfolioController` (+ `SnapshotStore` זמני)

**קוד קיים רלוונטי:**
```java
// PortfolioJson.java שורות 30-44 — שמות ורצף השדות שה-UI מכיר
{"symbol", "secType", "currency", "position", "averageCost", "marketPrice", "marketValue",
 "unrealizedPnl", "realizedPnl", "account", "costBasis", "unrealizedPnlPercent"}
// ברמה העליונה: account, asOf, netLiquidation, totalCashValue, holdings
// שורה 47: number() — NaN/∞ נכתב כ-null
// ApiServer.java שורה 42: Cache-Control: no-store
```

**המימוש:**
```java
@Component
public class SnapshotStore {                       // זמני — יימחק בסשן 2
    private volatile PortfolioSnapshot snapshot;
    public void set(PortfolioSnapshot snapshot) { this.snapshot = snapshot; }
    public Optional<PortfolioSnapshot> current() { return Optional.ofNullable(snapshot); }
}

@RestController
class PortfolioController {
    @GetMapping("/api/portfolio")
    ResponseEntity<PortfolioResponse> portfolio() {
        return snapshotStore.current()
                .map(snapshot -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PortfolioResponse.from(snapshot)))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }
}
```
`PortfolioResponse` / `HoldingResponse` — records עם **אותם שמות שדות**; `Double` (boxed) לכל מספר.

**⚠️ NaN:** Jackson מסדרג `Double.NaN` כ-**מחרוזת `"NaN"`**, לא כ-`null` → זה שובר את `netLiquidation: number | null` ב-UI.
הכלל מ-`PortfolioJson.number()` חייב לעבור: `Double.isFinite(value) ? value : null` (helper יחיד `finiteOrNull`).
**⚠️ `asOf`:** חייב לצאת כמחרוזת ISO (`"2026-09-19T08:05:00Z"`), כמו `snapshot.asOf().toString()` היום. ברירת המחדל של Spring Boot כזו —
לאמת ב-diff (בדיקת האימות).
**בסוף הסשן** (אחרי שה-diff נקי): למחוק `ApiServer.java` ו-`PortfolioJson.java`.
✅ נמחקו ב-21.09.2026 (יחד עם ה-`JsonParityTest` הזמני). לפני המחיקה הושוו פלט Jackson והכותב הישן בבדיקה חד-פעמית — NaN/∞ ← `null`, escaping, `asOf` עם שברי שנייה — ועברו. ה-records יושבים ב-`portfolioboss.api.response`. ה-diff החי מול TWS עדיין פתוח.

---

### ✅ 0.5 🟢 `run.sh` → Maven

**קוד קיים רלוונטי:** `run.sh` שורות 25-38 (בניית classpath, `javac`, `java -cp`).

**המימוש:** נשארים: בדיקת קיום ה-jar (שורות 20-23), `cd "${HERE}"` (Main מחפש `ui/` יחסית לתיקיית העבודה),
ושימוש זהה (`./run.sh 7497 102`). נמחקים: בניית `CP` של protobuf, `javac`, `out/`. נוסף: 0.2, ואז:
```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments="$*"
```

---

### ✅ 0.6 🟢 בדיקת JUnit ראשונה

**המימוש:** `src/test/java/portfolioboss/model/HoldingTest.java` — `costBasis()` ו-`unrealizedPnlPercent()`
(כולל מקרה `costBasis == 0` → `0.0`, שורה 35 ב-`Holding`). זו "בדיקת ה-JUnit הראשונה" של Milestone 1. `mvn -q test`.

---

**בדיקת אימות סשן 0:**
1. **לפני** שמתחילים: כש-`run.sh` הישן רץ מול TWS — `curl -s localhost:8080/api/portfolio > before.json`.
2. אחרי הסשן: `after.json` באותה דרך; `diff <(jq -S . before.json) <(jq -S . after.json)` — רק `asOf` רשאי להיות שונה.
3. ✅ `mvn -q test` ירוק; ✅ `cd ui && npm run build` ירוק; ✅ ה-UI מציג אותה טבלה — נבדק מול TWS דלוק ב-21.09.2026 (אישור המשתמש; ה-diff הפורמלי של before/after לא תועד).
4. ✅ **בלי TWS:** אותן הודעות שגיאה ויציאה נקייה (קוד 0) — נבדק ב-21.09.2026. נוספו שורות לוג של Spring (עלייה וכיבוי של Tomcat) סביב הודעות השגיאה.

---

## סשן 1 — PostgreSQL + Flyway + סכמה + ישויות JPA

> **תנאי מוקדם:** סשן 0. **המטרה:** DB אמיתי עם סכמה, שהאפליקציה מתחברת אליו ומאמתת. **עדיין אין סנכרון.**
>
> **סטטוס (21.09.2026):** 1.1–1.5 ✅ בוצעו על branch `maven-spring-boot` (עדיין לא committed). סטיות מהתוכנית שמופיעה למטה:
> - **עמודות IB הן `DOUBLE PRECISION`**, לא `NUMERIC(20,6)` (גם ב-`account_state`); `NUMERIC` נשאר רק ל-`trade.quantity` / `price`.
>   נבדק: `ddl-auto=validate` נכשל על שדה `Double` מול עמודת `NUMERIC` (`wrong column type encountered in column [average_cost]`).
> - **החבילה היא `portfolioboss.db`**, לא `persistence` — עקבית עם `ib` / `api` / `ui` ועם תגית הלוג `[db]`.
> - `HoldingEntity.position` הוא `double` (העמודה `NOT NULL`), לא `Double`. `trade.created_at` לא ממופה ב-`TradeEntity` (ברירת המחדל של ה-DB ממלאת אותו).
> - נוצר רק `HoldingRepository`; `TradeRepository` ו-`AccountStateRepository` ייווצרו כשיהיה להם שימוש (סשנים 2 ו-4).
> - **נדחו:** `spring-boot-starter-validation` — לסשן 4 (`@Valid`); סטארטרי ה-`*-test` של JPA ו-Flyway — לסשן 2 (`@DataJpaTest`).
> - `psql` לא ב-PATH (הנוסחה `postgresql@17` היא keg-only): לקרוא לו ב-`/opt/homebrew/opt/postgresql@17/bin/`, או להוסיף את התיקייה ל-PATH.

### ✅ 1.1 🟢 התקנת PostgreSQL והקמת מסדי נתונים

**המימוש:**
```bash
brew install postgresql@17
brew services start postgresql@17        # רץ ברקע ומתחיל עם ההתחברות למחשב, עד brew services stop
createdb portfolioboss
createdb portfolioboss_test              # לבדיקות בלבד — לעולם לא לכוון בדיקות ל-DB האמיתי
```
**⚠️** אם `psql` לא נמצא אחרי ההתקנה — להוסיף ל-PATH את `$(brew --prefix postgresql@17)/bin`.
**⚠️ אימות:** ברירת המחדל של Homebrew מאפשרת חיבור מקומי בלי סיסמה, עם משתמש = שם המשתמש ב-macOS —
**אין סוד שצריך לשמור ב-repo.** בכל זאת ה-datasource קורא משתני סביבה (ראה 1.4) למקרה שיהיה צורך.

---

### ✅ 1.2 🟡 מיגרציית Flyway `V1__portfolio_schema.sql`

**קובץ חדש:** `src/main/resources/db/migration/V1__portfolio_schema.sql`

**המימוש:**
```sql
CREATE TABLE holding (
    id                 BIGSERIAL PRIMARY KEY,
    account            VARCHAR(32)   NOT NULL,
    con_id             INTEGER       NOT NULL,          -- מזהה יציב של IB (symbol יכול להשתנות)
    symbol             VARCHAR(32)   NOT NULL,
    sec_type           VARCHAR(16)   NOT NULL,
    currency           VARCHAR(8)    NOT NULL,
    -- מוזן ע"י המשתמש; הסנכרון לעולם לא דורס
    sector             VARCHAR(60),
    -- מ-IB, מתעדכן בכל חיבור
    position           NUMERIC(20,6) NOT NULL DEFAULT 0,
    average_cost       NUMERIC(20,6),
    market_price       NUMERIC(20,6),
    market_value       NUMERIC(20,6),
    unrealized_pnl     NUMERIC(20,6),
    realized_pnl       NUMERIC(20,6),
    status             VARCHAR(6)    NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    first_seen_at      TIMESTAMPTZ   NOT NULL,
    last_synced_at     TIMESTAMPTZ   NOT NULL,
    closed_detected_at TIMESTAMPTZ,
    UNIQUE (account, con_id)
);

CREATE TABLE trade (
    id          BIGSERIAL PRIMARY KEY,
    holding_id  BIGINT        NOT NULL REFERENCES holding (id),
    trade_date  DATE          NOT NULL,
    side        VARCHAR(4)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity    NUMERIC(20,6) NOT NULL CHECK (quantity > 0),
    price       NUMERIC(20,6) CHECK (price >= 0),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX trade_holding_date ON trade (holding_id, trade_date);

CREATE TABLE account_state (
    account           VARCHAR(32) PRIMARY KEY,
    as_of             TIMESTAMPTZ NOT NULL,
    net_liquidation   NUMERIC(20,6),
    total_cash_value  NUMERIC(20,6)
);
```

**למה כך:**
- הטבלה נקראת **`trade`** ולא `transaction` (מילת מפתח ב-SQL).
- `NUMERIC` ולא `double` לכמויות שהמשתמש מזין — סכום עסקאות חייב להיות מדויק (מניות חלקיות: `10.5`).
- `UNIQUE (account, con_id)` — עולה כלום, ופותר מראש את השאלה הפתוחה ב-TODO.md על כמה חשבונות.
- אין `ON DELETE CASCADE` ואין מחיקת `holding` באפליקציה — לעולם.
- `account_state` שורה אחת לכל חשבון (upsert): מחזיקה את `asOf`, `netLiquidation`, `totalCashValue` שהיום נמצאים ב-`PortfolioSnapshot`.
  **היסטוריית NAV** (TWR) היא פריט נפרד ב-TODO.md ולא כאן.

**⚠️ מיגרציה שהורצה — לא עורכים.** כל שינוי עתידי = `V2__...sql` חדש. עריכת V1 אחרי ההרצה הראשונה תשבור את ה-checksum.

---

### ✅ 1.3 🟡 ישויות JPA ו-repositories (`portfolioboss.db`)

**המימוש:** `HoldingEntity`, `TradeEntity`, `AccountStateEntity`, `enum HoldingStatus { OPEN, CLOSED }`, `enum TradeSide { BUY, SELL }`
(`@Enumerated(EnumType.STRING)`). תבנית:
```java
@Entity
@Table(name = "holding")
public class HoldingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String account;
    private int conId;                       // Spring ממפה camelCase → con_id אוטומטית
    private String symbol;
    private String sector;                   // מוזן ביד
    private Double position;                 // מ-IB — Double
    ...
    @Enumerated(EnumType.STRING)
    private HoldingStatus status;

    @OneToMany(mappedBy = "holding")
    @OrderBy("tradeDate, id")
    private List<TradeEntity> trades = new ArrayList<>();

    protected HoldingEntity() { }            // JPA
}
```
```java
public interface HoldingRepository extends JpaRepository<HoldingEntity, Long> {
    Optional<HoldingEntity> findByAccountAndConId(String account, int conId);
    List<HoldingEntity> findByAccountAndStatus(String account, HoldingStatus status);
}
```
**⚠️ סוגי מספרים:** נתוני IB → `Double` (IB מחזיר `double` ממילא); נתונים שהמשתמש מזין (`TradeEntity.quantity` / `price`) → `BigDecimal`.
**⚠️** `TradeEntity.holding` — `@ManyToOne(fetch = LAZY)`.

---

### ✅ 1.4 🟢 `application.properties`

```properties
spring.application.name=portfolioboss
spring.main.banner-mode=off

server.address=127.0.0.1
server.port=8080

spring.datasource.url=jdbc:postgresql://localhost:5432/portfolioboss
spring.datasource.username=${PORTFOLIOBOSS_DB_USER:${user.name}}
spring.datasource.password=${PORTFOLIOBOSS_DB_PASSWORD:}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
```
**⚠️ `ddl-auto=validate`** — Hibernate בודק בעליית האפליקציה שהסכמה תואמת לישויות, ולא משנה כלום. **לעולם לא `update` / `create`** —
הסכימה משתנה רק דרך Flyway.
**⚠️ `open-in-view=false`** — בלי lazy-loading "בשקט" בשכבת ה-API; מי שצריך `trades` טוען אותם במפורש (סשן 3).
**תלויות ל-pom:** `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `postgresql` (runtime),
`spring-boot-starter-validation` + סטארטרי `*-test` התואמים (ראה 0.1: ליצור מ-Initializr).

---

### ✅ 1.5 🟢 גיבוי — `scripts/backup-db.sh`

**הבעיה:** הנתונים הידניים הם המוצר ואי אפשר לשחזר אותם מ-IB.

**המימוש:**
```bash
mkdir -p "${HOME}/PortfolioBossBackups"
pg_dump portfolioboss | gzip > "${HOME}/PortfolioBossBackups/portfolioboss-$(date +%F).sql.gz"
```
**⚠️** הגיבוי מחוץ ל-repo (מכיל נתוני פורטפוליו). **לבדוק שחזור פעם אחת:** `gunzip -c <file> | psql portfolioboss_test`.

---

**בדיקת אימות סשן 1:** `./run.sh` עולה; בלוג `Successfully applied 1 migration`; `psql portfolioboss -c '\dt'` מציג `holding`, `trade`,
`account_state`, `flyway_schema_history`. עם Postgres כבוי (`brew services stop postgresql@17`) — האפליקציה נכשלת מהר עם שגיאה ברורה.
ה-UI והתנהגות ה-TWS — ללא שינוי.

**סטטוס הבדיקה (21.09.2026):**
1. ✅ `./run.sh` עולה; `Successfully applied 1 migration ... now at version v1`; `\dt` מציג `holding`, `trade`, `account_state`, `flyway_schema_history`; `mvn -q test` ירוק. הורץ עם פורט TWS שלא קיים (`./run.sh 7599`), כדי שהחזקות אמיתיות לא יודפסו לשיחה.
2. ✅ DB לא זמין → נכשל תוך 3 שניות, קוד יציאה 1, `Connection to localhost:5999 refused`. נבדק בהפניית ה-datasource לפורט שלא מאזין (`SPRING_DATASOURCE_URL=... ./run.sh`), לא בכיבוי השירות. הפלט הוא stack trace של Spring (~105 שורות) וההודעה בראשו.
3. ✅ `validate` נבדק שלילית על `portfolioboss_test` (עמודה ששונתה ל-`NUMERIC` → כשל בעלייה); מסד הבדיקות נוצר מחדש נקי. ✅ גיבוי ושחזור ל-`portfolioboss_test` נבדקו.
4. ⬜ **פתוח:** ריצה רגילה של `./run.sh` מול TWS דלוק, כדי לראות שה-UI מציג אותה טבלה כמו קודם.

---

## סשן 2 — סנכרון בחיבור + ה-API קורא מה-DB

> **תנאי מוקדם:** סשן 1. **המטרה:** בכל חיבור ל-TWS ה-DB מתעדכן, וה-API מגיש **רק מה-DB**.
>
> **סטטוס (22.09.2026):** 2.1–2.5 ✅ בוצעו על branch `maven-spring-boot`, עדיין לא committed. סטיות מהתוכנית:
> - השירות הקורא נקרא **`PortfolioReadService`**, לא `PortfolioQueryService` (המלצה שאושרה).
> - סדר המימוש: 2.1, ואז 2.2+2.5 (הסנכרון ובדיקותיו) יחד, ואז 2.4+2.3 יחד — אי אפשר היה למחוק את `SnapshotStore`
>   לפני ששני הצדדים (הקורא והכותב) עברו לדבר עם ה-DB.
> - `HoldingEntity` / `AccountStateEntity` קיבלו accessors ציבוריים בסגנון record ו-`HoldingEntity.toIbHolding()`,
>   כך ש-`costBasis` / `unrealizedPnlPercent` נשארים מחושבים פעם אחת, ב-`Holding`.
> - העוזר המשותף ל-JSON ול-DB (`finiteOrNull`, ובכיוון ההפוך `nanIfNull`) יושב ב-**`portfolioboss.utils.Utils`**
>   (השם `Figures` נדחה — "שם גרוע מאוד"; `Utils` הוא הדפוס הקיים ב-IBBot). `JsonNumbers` נמחק.
> - נוספו `PortfolioWrapperTest` ו-`PortfolioReadServiceTest`, לא בתוכנית המקורית.
> - **חריגה מכוונת מהתוכנית, ביוזמת עומרי (22.09.2026):** יישמנו כבר עכשיו את הליבה של **סשן 9** למטה —
>   TWS לא זמין כבר לא מפיל את האפליקציה. 9.2/9.3 (דגל stale + באנר ב-UI) נשארו בכוונה לסשן 9 עצמו.

### ✅ 2.1 🟢 `Holding` מקבל `conId`; `PortfolioWrapper` ממפה לפיו

**קוד קיים רלוונטי:**
```java
// Holding.java שורות 15-25 — אין conId
public record Holding(String symbol, String secType, String currency, double position, ...

// PortfolioWrapper.java שורה 36 — המפתח הוא symbol
private final Map<String, Holding> holdings = new LinkedHashMap<>();

// שורות 83-92
holdings.remove(contract.symbol());   // a closed-out position
holdings.put(contract.symbol(), new Holding(contract.symbol(), ...));
```

**התיקון:** `int conId` כרכיב שני ב-`Holding`; `Map<Integer, Holding>`; המפתח `contract.conid()`.
**⚠️** זה גם סוגר את הסיכון שמסומן ב-reference_ui.md ("שתי פוזיציות עם אותו symbol מתנגשות"). לחפש `new Holding(` — אחרי סשן 0 רק
`PortfolioWrapper` והבדיקות בונים אותו. דוח הקונסול לא משתנה.

---

### ✅ 2.2 🔴 `PortfolioSyncService` — הליבה של הסנכרון

**קובץ חדש:** `src/main/java/portfolioboss/db/PortfolioSyncService.java`

**המימוש:**
```java
@Service
public class PortfolioSyncService {

    @Transactional
    public SyncResult sync(PortfolioSnapshot snapshot) {
        Instant syncedAt = snapshot.asOf();
        Set<Integer> seenConIds = new HashSet<>();
        int added = 0;
        int updated = 0;

        for (Holding reading : snapshot.holdings()) {
            seenConIds.add(reading.conId());
            Optional<HoldingEntity> stored = holdings.findByAccountAndConId(snapshot.account(), reading.conId());
            if (stored.isPresent()) {
                stored.get().refreshFromIb(reading, syncedAt);          // שדות IB + status=OPEN; sector ו-trades לא נוגעים
                updated++;
            } else {
                holdings.save(HoldingEntity.firstSeen(snapshot.account(), reading, syncedAt));
                added++;
            }
        }

        int closed = markMissingAsClosed(snapshot.account(), seenConIds, syncedAt);
        accountStates.save(AccountStateEntity.of(snapshot));
        System.out.printf("[db] synced %d holdings (%d new, %d updated, %d closed)%n", seenConIds.size(), added, updated, closed);
        return new SyncResult(added, updated, closed);
    }
}
```
- `refreshFromIb`: `symbol`, `position`, `averageCost`, `marketPrice`, `marketValue`, `unrealizedPnl`, `realizedPnl`, `status = OPEN`,
  `lastSyncedAt`, `closedDetectedAt = null`. **לא נוגע ב-`sector`.**
- `markMissingAsClosed`: לכל `OPEN` של החשבון שה-`conId` שלו לא נראה → `status = CLOSED`, `position = 0`, `marketValue = 0`,
  `unrealizedPnl = 0`, `closedDetectedAt = syncedAt`. שאר העמודות (`averageCost`, `marketPrice`, `realizedPnl`) נשארות כפי שנראו לאחרונה.
- helper יחיד `finiteOrNull(double)` — NaN ← `NULL` בעמודה.

**⚠️ סימון `CLOSED` הפיך:** אם ההחזקה מופיעה שוב — חוזרת ל-`OPEN`, ושום נתון ידני לא אבד. זה גם הגנה מפני קריאת IB חלקית.
**⚠️** הסנכרון רץ רק עם `snapshot != null`, כלומר אחרי ש-`accountDownloadEnd` ירה (`PortfolioWrapper.snapshot()` הוא `null` עד אז).
**⚠️** `snapshot.account()` יכול להיות `""` — העמודה `NOT NULL` וזה תקין.

---

### ✅ 2.3 🟡 `Main`: סנכרון במקום `SnapshotStore`

**המימוש:** ב-`ApplicationRunner` (0.3): `snapshotStore.set(snapshot)` ← `syncService.sync(snapshot)`. **למחוק את `SnapshotStore`.**
אם הסנכרון זורק (שגיאת DB) — `[db error] ...` ויציאה עם קוד 1: בלי DB האפליקציה לא יכולה לעבוד.
**עודכן בפועל (22.09.2026):** המשפט "יציאה על TWS לא זמין — נשארת עד סשן 9" **כבר לא נכון** — ראו הליבה
של סשן 9 למטה, שבוצעה מוקדם באותו יום ביוזמת עומרי.

---

### ✅ 2.4 🟡 `PortfolioReadService` (בתוכנית: `PortfolioQueryService`) + ה-DTOs — ה-API קורא מה-DB

**המימוש:**
- `PortfolioResponse(account, asOf, netLiquidation, totalCashValue, holdings)` — `asOf` מ-`account_state.as_of`.
- `HoldingResponse`: **כל השדות הקיימים בשמותיהם**, ובנוסף `id`, `conId`, `sector`, `status`.
- `costBasis` / `unrealizedPnlPercent` — **נשארים מחושבים ב-Java** (reference_ui.md: "not re-derived in the UI"), **בלי לשכפל את הנוסחאות**:
  `HoldingEntity.toIbHolding()` בונה מחדש את ה-record `Holding` מהנתונים השמורים (`null` ← `NaN`), ו-`HoldingResponse.from(...)` קורא ל-`costBasis()` / `unrealizedPnlPercent()` שלו.
- ה-payload כולל `OPEN` ו-`CLOSED` (עם `status`) — ה-UI מסנן (סשן 5).
- אם אין שורה ב-`account_state` → 503 (לא אמור לקרות: הסנכרון נכשל = האפליקציה יצאה).
- `@Transactional(readOnly = true)` — לבנות את ה-DTOs *בתוך* הטרנזקציה (`open-in-view=false`).

**⚠️** בחשבון יחיד לוקחים את החשבון של שורת `account_state` האחרונה. (כמה חשבונות — שאלה פתוחה ב-TODO.md.)

---

### ✅ 2.5 🟡 בדיקות סנכרון (JUnit מול DB אמיתי)

**הגדרה:** `src/test/resources/application-test.properties` → `jdbc:postgresql://localhost:5432/portfolioboss_test`, `@ActiveProfiles("test")`.
**⚠️ בדיקות לעולם לא רצות מול `portfolioboss`.** שימוש ב-`@DataJpaTest` עם `@AutoConfigureTestDatabase(replace = NONE)` (כדי לא לעבור ל-DB מוטמע) + `@Import(PortfolioSyncService.class)`;
ב-Spring Boot 4 חבילות ה-import של הטסטים עברו לפי מודולים — לקחת את ה-FQCN מה-IDE. rollback אוטומטי בכל בדיקה.

**בדיקות ל-`PortfolioSyncServiceTest`:**
1. החזקה חדשה → נשמרת, `sector = null`, `status = OPEN`
2. החזקה קיימת → נתוני IB מתעדכנים, **`sector` ו-`trades` לא משתנים**
3. החזקה שנעלמה → `CLOSED`, `position = 0`, השורה והעסקאות עדיין קיימות
4. החזקה `CLOSED` שחוזרת → `OPEN`, `closedDetectedAt = null`
5. נתון `NaN` → `NULL` בעמודה
6. שתי החזקות עם אותו symbol וב-`conId` שונה → שתי שורות

---

**בדיקת אימות סשן 2 (מול TWS מחובר):**
1. ריצה ראשונה: `[db] synced N holdings (N new, 0 updated, 0 closed)`; ריצה שנייה: `(0 new, N updated, 0 closed)`.
2. `psql portfolioboss -c "select symbol, position, status from holding"` תואם ל-TWS.
3. `update holding set sector='Test' where symbol='...'` → ריצה נוספת → הסקטור נשאר.
4. ה-UI מציג אותה טבלה כמו קודם (השדות החדשים עדיין לא מוצגים). `diff` מול `before.json` מסשן 0 — נוספו רק `id`, `conId`, `sector`, `status`.
5. ~~בלי TWS: יציאה עם ההודעה הישנה.~~ **שונה ב-22.09.2026** — ראו הסטטוס למעלה: בלי TWS האפליקציה עולה
   כרגיל ומגישה את הסנכרון האחרון, במקום לצאת.

**סטטוס הבדיקה (22.09.2026, חלקי):**
1. ✅ ריצה ראשונה מול TWS חי: `holding` ב-`portfolioboss` (האמיתי) מכיל 7 שורות `OPEN`, שורת `account_state` אחת.
   ⬜ ריצה שנייה מול TWS חי (כדי לראות `updated` ולא `new`) — עוד לא בוצעה.
2. ⬜ פתוח — עומרי דחה לסשן 4.
3. ⬜ פתוח — עומרי דחה לסשן 4 (אין עדיין דרך להזין סקטור מלבד `psql` ישירות; ה-endpoint הכותב מגיע רק שם).
4. ⬜ פתוח חלקית: אומת ש-`GET /api/portfolio` מחזיר 200 עם `id`/`conId`/`sector`/`status` על גבי הנתונים האמיתיים;
   לא בוצעה השוואה חזותית מול ה-UI הפתוח בדפדפן ולא `diff` מול `before.json`.
5. ✅ אומת פעמיים: (א) TWS כבוי **לפני** השינוי — יציאה עם ההודעה הישנה, ה-DB לא נגע. (ב) TWS כבוי **אחרי**
   השינוי, מול אותו DB עם 7 השורות מ-(1) — האפליקציה עלתה, הדפיסה
   `[db] TWS unreachable; serving the portfolio from the last sync, if any`, ו-`/api/portfolio` החזיר את
   אותם 7 holdings ואת אותו `asOf` כמו הסנכרון האחרון.

---

## סשן 3 — נגזרות: תאריך קנייה, תאריך מכירה, זמן החזקה

> **תנאי מוקדם:** סשן 2. **המטרה:** לחשב מהעסקאות ולהגיש ב-API. **עדיין אין כתיבה ולא UI** — עסקאות נכנסות כרגע רק דרך `psql`.

### 3.1 🟡 `HoldingHistory` — חישוב טהור בלי Spring (`portfolioboss.domain`)

**הבעיה:** "זמן החזקה" הוא המדד המרכזי, והוא נשבר בשקט במקרה נפוץ אצל משקיע ארוך-טווח: **מכירה מלאה וקנייה מחדש** של אותה מניה.
תאריך הקנייה הראשון הוא אז של פוזיציה אחרת.

**הגדרות (ברירת מחדל — המשתמש יכול לשנות):**
- **Episode** = הרצף מאז שהפוזיציה הייתה אפסית לאחרונה. מריצים את העסקאות בסדר כרונולוגי עם כמות רצה `q`;
  כש-SELL מוריד את `q` ל-0 (או פחות, בסבלנות `0.000001`) — ה-episode נסגר; ה-BUY הבא פותח חדש.
- **באותו תאריך:** BUY לפני SELL (אחרת קנייה ומכירה באותו יום נותנות `q` שלילי בגלל סדר ההזנה בלבד).
- `firstBuyDate` = תאריך ה-BUY הראשון ב-**episode הנוכחי** (האחרון). `null` אם אין BUY.
- `lastSellDate` = תאריך ה-SELL האחרון **באותו episode**. `null` אם אין.
- `holdingDays`: `OPEN` → מ-`firstBuyDate` עד **תאריך ה-snapshot** (`account_state.as_of`, לא שעון המערכת — כך המספר עקבי עם מה שהמשתמש רואה, גם כשהנתונים ישנים);
  `CLOSED` → מ-`firstBuyDate` עד `lastSellDate`, ו-`null` אם לא הוזנה מכירה.
- SELL כשהפוזיציה אפסית (טעות הזנה) — נתעלם ממנו לצורך ה-episode; לא נחסום אותו בשרת (המשתמש אולי לא הזין את הקניות הקודמות).

**המימוש:** קלט `List<TradeFact>` (`record TradeFact(LocalDate date, TradeSide side, BigDecimal quantity)` — המרה מ-`TradeEntity` במפה, כדי שהמחלקה תישאר טהורה);
פלט `record HoldingHistory(LocalDate firstBuyDate, LocalDate lastSellDate, BigDecimal netQuantity)` ומתודה `Long holdingDays(HoldingStatus status, LocalDate snapshotDate)`.
**⚠️** לא נשמר ב-DB — מחושב בכל קריאה (מספר קטן של עסקאות לכל החזקה).

---

### 3.2 🟡 טעינת עסקאות בלי N+1

**המימוש:** ב-`HoldingRepository`: `@EntityGraph(attributePaths = "trades")` על השאילתה ש-`PortfolioQueryService` משתמש בה (שאילתה אחת עם join fetch).

---

### 3.3 🟡 הרחבת `HoldingResponse`

**המימוש (תוספות בלבד):**
```java
LocalDate firstBuyDate,        // "2024-03-14" | null
LocalDate lastSellDate,
Long holdingDays,
List<TradeResponse> trades     // מסודר לפי תאריך ואז id
// record TradeResponse(long id, LocalDate tradeDate, TradeSide side, BigDecimal quantity, BigDecimal price, String note)
```
**⚠️** תוספת בלבד — ה-UI הישן מתעלם משדות שהוא לא מכיר. תאריכים כ-ISO (`yyyy-MM-dd`); `BigDecimal` ← מספר JSON.

---

### 3.4 🟢 `HoldingHistoryTest` (טהור — בלי Spring, בלי DB)

1. אין עסקאות → הכול `null`
2. קנייה אחת → `firstBuyDate` נכון, `lastSellDate = null`, `holdingDays` = הפרש הימים
3. קנייה + מכירה חלקית → `firstBuyDate` זהה, `lastSellDate` = תאריך המכירה, `OPEN` ממשיך לספור
4. קנייה + מכירה מלאה (`CLOSED`) → `holdingDays` = מכירה − קנייה
5. קנייה, מכירה מלאה, קנייה מחדש → **ה-episode מתאפס**: `firstBuyDate` = הקנייה השנייה
6. שתי קניות (ממוצעים) → `firstBuyDate` = המוקדמת
7. קנייה ומכירה באותו יום → BUY מעובד ראשון, `q = 0`
8. `CLOSED` בלי מכירה מוזנת → `holdingDays = null`
9. כמויות חלקיות: קנייה `10.5`, מכירה `10.5` → אפסי (השוואת `BigDecimal` בסבלנות, לא `equals`)

---

**בדיקת אימות סשן 3:** `insert into trade (holding_id, trade_date, side, quantity) values (...)` דרך `psql` →
`curl -s localhost:8080/api/portfolio | jq '.holdings[] | select(.symbol=="X") | {firstBuyDate, lastSellDate, holdingDays, trades}'`.

---

## סשן 4 — endpoints כותבים (סקטור ועסקאות)

> **תנאי מוקדם:** סשן 3. **⚠️ זה הסשן ששובר במכוון את הכלל ב-CLAUDE.md** "`ApiServer` has one GET endpoint … Don't add an endpoint that changes anything".

### 4.1 🟡 החלטה מתועדת: ניסוח מחדש של ה-invariant

**קוד קיים רלוונטי (CLAUDE.md):** "The local API is covered by the same rule: `ApiServer` has one GET endpoint and binds to the loopback interface only. Don't add an endpoint that changes anything…"

**הבעיה:** הכלל נכתב כדי להגן על החשבון, אבל הוא מנוסח רחב מדי; עסקאות וסקטור הם נתונים מקומיים בלבד.

**ניסוח מוצע (יעודכן ב"עדכונים של סוף סשן", ביוזמת המשתמש):** "The API never touches the IB account. It writes only to PortfolioBoss's own
database (sector, trades). It binds to loopback only, has no CORS configuration, and every write endpoint accepts JSON only."
לעדכן במקביל: README ("loopback only, GET only"), ו-reference_ui.md.

---

### 4.2 🟡 `HoldingWriteController` + `TradeService`

**המימוש:**
```java
@RestController
@RequestMapping("/api")
class HoldingWriteController {

    @PutMapping(path = "/holdings/{holdingId}/sector", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> setSector(@PathVariable long holdingId, @Valid @RequestBody SectorRequest request) { ... 204 }

    @PostMapping(path = "/holdings/{holdingId}/trades", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TradeResponse> addTrade(@PathVariable long holdingId, @Valid @RequestBody TradeRequest request) { ... 201 }

    @PutMapping(path = "/trades/{tradeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TradeResponse> updateTrade(@PathVariable long tradeId, @Valid @RequestBody TradeRequest request) { ... }

    @DeleteMapping("/trades/{tradeId}")
    ResponseEntity<Void> deleteTrade(@PathVariable long tradeId) { ... 204 }
}

record SectorRequest(@Size(max = 60) String sector) { }        // ריק/רווחים → נשמר כ-null
record TradeRequest(
        @NotNull @PastOrPresent LocalDate tradeDate,
        @NotNull TradeSide side,
        @NotNull @Positive BigDecimal quantity,
        @PositiveOrZero BigDecimal price,
        @Size(max = 500) String note) { }
```
- `TradeService` (`@Transactional`): ההחזקה חייבת להתקיים (404); sector: `trim`, ריק ← `null`.
- אחרי כתיבה ה-UI טוען מחדש את כל `/api/portfolio` — ה-endpoints נשארים קטנים.
- **⚠️ אין endpoint שיוצר או מוחק החזקות** — החזקות נולדות רק מסנכרון IB. **מגבלה ידועה:** פוזיציה שנמכרה לגמרי *לפני* הסנכרון הראשון לא קיימת ב-DB ולא ניתן להזין לה עסקאות.
- **⚠️** סקטור הוא טקסט חופשי; ה-UI מציע ערכים קיימים (סשן 6) כדי לצמצם סטיות כתיב.

---

### 4.3 🟡 טיפול בשגיאות — `ApiErrors`

**הבעיה:** ה-UI צריך להציג הודעה שימושית, אבל `ProblemDetail` ברירת המחדל של שגיאות ולידציה הוא רק `"Invalid request content."`.

**המימוש:**
```java
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {
    // override של handleMethodArgumentNotValid: detail = "quantity: must be greater than 0" (שדה: הודעה, מופרדים בפסיק)
    // @ExceptionHandler(NotFoundException.class) → 404 עם ProblemDetail
}
```
JSON פגום (`HttpMessageNotReadableException`) — נכלל בבסיס (400).

---

### 4.4 🔴 עמדת אבטחה — בדיקות

**הבעיה:** ברגע שה-API כותב, דף אינטרנט זדוני יכול לנסות לשלוח בקשות ל-`localhost:8080` מהדפדפן.

**ההגנות (כולן חלק מהמימוש, לא "אופציונלי"):**
- `server.address=127.0.0.1` (מ-0.3) — לא נגיש מהרשת. ידנית: `curl http://<כתובת-ה-LAN>:8080/api/portfolio` מסורב.
- **אין שום הגדרת CORS.** בקשות `PUT`/`DELETE` ובקשות `application/json` דורשות preflight — והוא נחסם. בדפדפן ה-UI עובד מול אותו origin דרך ה-proxy של Vite (`5174 → 8080`).
- `consumes = APPLICATION_JSON_VALUE` — `POST` "פשוט" (`text/plain` / form) מקבל 415.
- ⚠️ בדיקת `Origin` נוספת — הקשחה לא נחוצה כרגע.

---

### 4.5 🟡 בדיקות `HoldingWriteControllerTest` (MockMvc, מול ה-DB של הבדיקות)

עסקה תקינה → 201 ונשמרת; תאריך עתידי → 400; כמות `0` → 400; `side` לא מוכר → 400; החזקה לא קיימת → 404;
סקטור ריק → `null`; `text/plain` → 415; מחיקה כפולה → 404 בפעם השנייה; הודעת השגיאה מכילה את שם השדה.

---

**בדיקת אימות סשן 4:** `curl -X POST -H 'Content-Type: application/json' -d '{"tradeDate":"2025-01-15","side":"BUY","quantity":10}' localhost:8080/api/holdings/1/trades`
→ 201; שורה ב-`psql`; `GET /api/portfolio` מחזיר אותה. אותה בקשה עם `-H 'Content-Type: text/plain'` → 415.

---

## סשן 5 — UI: צד הקריאה (types, פורמט, עמודות, מיון)

> **תנאי מוקדם:** סשן 3 (סשן 4 לא הכרחי לזה, אבל יאפשר לבדוק בקלות).

### 5.1 🟢 `ui/src/types/portfolio.ts`

**קוד קיים רלוונטי:** `Holding` שורות 3-16; ההערה בשורה 1 מפנה ל-`PortfolioJson.java` (שנמחק בסשן 0).

**התיקון:**
```ts
export type HoldingStatus = 'OPEN' | 'CLOSED';
export type TradeSide = 'BUY' | 'SELL';

export interface Trade {
  id: number;
  tradeDate: string;              // 'yyyy-MM-dd'
  side: TradeSide;
  quantity: number;
  price: number | null;
  note: string | null;
}

export interface Holding {
  id: number;
  conId: number;
  sector: string | null;
  status: HoldingStatus;
  firstBuyDate: string | null;
  lastSellDate: string | null;
  holdingDays: number | null;
  trades: Trade[];
  // ...השדות הקיימים
}
```
ולעדכן את הערת הראש: "Mirrors `GET /api/portfolio` (see the `*Response` records in `api/`)".

---

### 5.2 🟢 `ui/src/lib/format.ts`

**המימוש:**
```ts
export const EMPTY_VALUE = '—';

/** 'yyyy-MM-dd' מוצג כמות שהוא (חד-משמעי וממויין כטקסט); null → '—'. */
export function formatDate(isoDate: string | null): string {
  return isoDate ?? EMPTY_VALUE;
}

/** 412 → "1y 1m", 45 → "1m 15d", 6 → "6d", null → '—'. חודשים מקורבים ל-30 ימים. */
export function formatHoldingPeriod(days: number | null): string { ... }
```
**⚠️** פורמט התאריך הוא ISO כברירת מחדל; אם המשתמש מעדיף `dd/MM/yyyy` — שינוי של שורה אחת ב-`formatDate`.

---

### 5.3 🟡 `PositionsTable.tsx` — עמודות חדשות ומיון

**קוד קיים רלוונטי:**
```ts
// שורות 14-21
type SortableField = 'symbol' | 'position' | 'averageCost' | ... | 'unrealizedPnlPercent';
// שורות 31-36 — מיון מספרי בלבד
function compareHoldings(first, second, field) {
  if (field === 'symbol') return first.symbol.localeCompare(second.symbol);
  return first[field] - second[field];
}
// שורות 38-43 — direction מוכפל *מבחוץ*
directionMultiplier * compareHoldings(first, second, sortState.field)
// שורה 195
<HoldingRow key={holding.symbol} holding={holding} />
```

**הבעיה:** עמודות חדשות הן טקסט (סקטור), תאריכים, ומספרים **שיכולים להיות ריקים**. ריקים חייבים להישאר בתחתית **בשני כיווני המיון** —
אם ההכפלה בכיוון נשארת בחוץ, הריקים יקפצו למעלה במיון יורד.

**המימוש:**
```ts
type SortableField = /* הקיימים */ | 'sector' | 'firstBuyDate' | 'lastSellDate' | 'holdingDays';

/** ערכים ריקים שוקעים תמיד לתחתית, בלי קשר לכיוון המיון. */
function compareNullableLast<T>(
  first: T | null, second: T | null,
  compareValues: (a: T, b: T) => number, directionMultiplier: number,
): number {
  if (first === null && second === null) return 0;
  if (first === null) return 1;
  if (second === null) return -1;
  return directionMultiplier * compareValues(first, second);
}
```
`compareHoldings` מקבל `directionMultiplier` ומחיל אותו בעצמו (במקום `sortHoldings`). מחרוזות ותאריכי ISO — `localeCompare`; `holdingDays` — הפרש.

עמודות חדשות ב-`COLUMNS` (`Sector` מיד אחרי `Symbol`; שלוש האחרות בסוף):
```ts
{ field: 'sector',       title: 'Sector',    alignment: 'left',  renderValue: (h) => h.sector ?? EMPTY_VALUE },
{ field: 'firstBuyDate', title: 'Bought',    alignment: 'right', renderValue: (h) => formatDate(h.firstBuyDate) },
{ field: 'lastSellDate', title: 'Last sold', alignment: 'right', renderValue: (h) => formatDate(h.lastSellDate) },
{ field: 'holdingDays',  title: 'Held',      alignment: 'right', renderValue: (h) => formatHoldingPeriod(h.holdingDays) },
```
**⚠️** `key` של השורה → `holding.id`. ההערה בשורה 65 ("Same columns … as the console report") כבר לא נכונה — לעדכן.
**⚠️** הטבלה גדלה מ-7 ל-11 עמודות; כבר יש `overflow-x-auto`.

---

### 5.4 🟢 `App.tsx` — סינון פתוחות + טקסטים

**קוד קיים רלוונטי:** שורות 65-67 (`sectionSubtitle`: "snapshot taken …"), שורה 74 ("Read-only view of your Interactive Brokers holdings."), שורה 79 (`SummaryBar`), שורה 58 (`PositionsTable`).

**התיקון:**
```ts
const openHoldings = snapshot.holdings.filter((holding) => holding.status === 'OPEN');
```
ל-`PositionsTable` ול-`SummaryBar` עוברות `openHoldings`. (להחזקות `CLOSED` יש אפסים בסכומים, אבל מסננים בכל מקרה.)
טקסטים: כותרת ← "Read-only against Interactive Brokers · sector and trades are stored locally."; תת-כותרת ← "last synced from TWS …".

---

**בדיקת אימות סשן 5:** `cd ui && npm run build` ירוק. בדפדפן: העמודות החדשות מופיעות; מיון בכל אחת בשני הכיוונים — **ריקים תמיד בתחתית**;
החזקה בלי עסקאות מציגה "—"; החזקה שהוספתי לה `BUY` דרך `curl`/`psql` מציגה תאריך וזמן החזקה.

---

## סשן 6 — UI: טפסי הזנה (סקטור, עסקאות, החזקות סגורות)

> **תנאי מוקדם:** סשנים 4 + 5.

### 6.1 🟢 `ui/src/lib/apiClient.ts`

**הבעיה:** `usePortfolio` מציג הודעה אחת לכל כשל (`API_UNREACHABLE_MESSAGE`). לכתיבה צריך את הודעת השרת ("quantity: must be greater than 0").

**המימוש:**
```ts
export class ApiError extends Error {}

export async function sendJson(method: 'PUT' | 'POST' | 'DELETE', url: string, body?: unknown): Promise<void> {
  const response = await fetch(url, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    throw new ApiError(await readProblemDetail(response));   // detail מה-ProblemDetail, או `The API answered HTTP ${status}`
  }
}
```
**⚠️** אין כאן react-query או ספרייה נוספת — `fetch` פשוט. אחרי כל כתיבה קוראים ל-`retry()` של `usePortfolio` (טוען מחדש את כל ה-snapshot).

---

### 6.2 🟡 `SectorCell` — עריכה בתוך הטבלה

**המימוש:** `components/SectorCell.tsx`. מצב צפייה: כפתור עם הסקטור או "+ add". מצב עריכה: `<input list="sector-options">`, `Enter`/`blur` שומר (`PUT /api/holdings/{id}/sector`)
אם השתנה, `Esc` מבטל, ריק מנקה; שגיאה מוצגת מתחת בוורוד; בזמן שמירה — disabled.
`<datalist id="sector-options">` נבנה פעם אחת ב-`PositionsTable` מהסקטורים השונים שכבר קיימים.

**⚠️** `renderValue(holding)` בשורה 60 מקבל רק את ההחזקה — צריך להוסיף הקשר: `renderValue(holding, { onDataChanged })`.
**⚠️** אחרי שמירה הרשימה ממוינת מחדש (אם ממוינים לפי סקטור) והשורה "קופצת" — מקובל.

---

### 6.3 🟡 שורה נפתחת + `TradesPanel`

**המימוש:** עמודה צרה בהתחלה עם כפתור chevron (`aria-expanded`); ב-`PositionsTable` state `expandedHoldingId: number | null` (אחת פתוחה בכל פעם);
כשפתוחה — `<tr><td colSpan={COLUMNS.length + 1}>` עם `TradesPanel`: רשימת העסקאות (תאריך, תג BUY ירוק / SELL אדום, כמות, מחיר, הערה) עם אייקוני עריכה (`Pencil`)
ומחיקה (`Trash2`, עם `window.confirm`), ומתחתיה `TradeForm`.

---

### 6.4 🟡 `TradeForm`

**המימוש:** שדות: תאריך (`<input type="date" max={היום}>`, ברירת מחדל היום), צד (שני כפתורים BUY / SELL), כמות (`type="number" step="any"`), מחיר (אופציונלי), הערה (אופציונלית, `maxLength=500`, placeholder "Why? (optional)").
יצירה → `POST /api/holdings/{id}/trades`; עריכה → `PUT /api/trades/{id}` עם שדות ממולאים. ולידציה בצד הלקוח משקפת את השרת (כמות > 0, תאריך קיים), **והשרת הוא הסמכות** — מציגים את `ApiError.message`.

**האצת ה-backfill:** להחזקה **בלי עסקאות** — ברירת מחדל `BUY`, כמות = `holding.position`, מחיר = `holding.averageCost` (כולם ניתנים לעריכה), כך שנשאר להזין רק תאריך.
**⚠️** `averageCost` של IB כולל עמלות ולכן הוא קירוב למחיר הביצוע — מסומן ב-placeholder כ-"IB avg cost"; ניתן לרוקן.
**⚠️** שדה ה-`note` הוא הזרע של ה-decision journal (עיקרון 6 ב-TODO.md) — בלי לבנות עוד מנגנון.

---

### 6.5 🟢 מתג "Show closed"

**המימוש:** checkbox בכותרת אזור ה-Positions; state ב-`App` (`showClosed`); הטבלה מקבלת `showClosed ? snapshot.holdings : openHoldings`.
שורות `CLOSED`: `opacity-60` ותג "closed" ליד הסימבול. עריכת עסקאות של החזקה סגורה מותרת (זה בדיוק המקום להשלים היסטוריה). `SummaryBar` תמיד על הפתוחות.

---

**בדיקת אימות סשן 6 (ידני בדפדפן):** להזין סקטור (`Enter` / blur / `Esc`; ריק מנקה) → נשמר אחרי רענון ואחרי הפעלה מחדש של `run.sh`;
להוסיף `BUY` → מתעדכנים "Bought" ו-"Held"; להוסיף `SELL` חלקי → "Last sold"; למחוק עסקה → חוזר; שגיאת ולידציה (כמות 0) מציגה את הודעת השרת;
מתג "Show closed"; החזקה ללא עסקאות מציעה BUY מוכן; `npm run build`.

---

## 🔮 סשנים 7–9 — רעיונות לעתיד (לא לפני שסשנים 0–6 יציבים)

> אלה ההצעות שאושרו כ"נחמדות להוסיף". הן נשענות על כך ש-**ה-API תמיד קורא מה-DB** (סשן 2).
> סשן 9 בלתי-תלוי בסשנים 3–8 ואפשר לעשות אותו כבר אחרי סשן 2.

## סשן 7 — [רעיון עתידי] אזהרות אי-התאמה (Reconciliation)

> **תנאי מוקדם:** סשן 6. **הרעיון:** IB יודע את הכמות האמיתית, והיומן הידני עלול לחסור או לטעות — להראות פער, בלי לחסום.

### 7.1 🟡 חישוב אזהרות ב-`HoldingHistory`

**המימוש:** `enum HoldingWarningType { NO_TRADES_LOGGED, QUANTITY_MISMATCH, CLOSED_WITHOUT_SELL }`.
- `NO_TRADES_LOGGED` — אין אף BUY (זו גם "רשימת ה-backfill").
- `QUANTITY_MISMATCH` — `OPEN`: סך כל ה-BUY פחות סך כל ה-SELL ≠ `position` של IB (סבלנות `0.0001`, כי מניות חלקיות מוצגות עד 4 ספרות); `CLOSED`: הסך ≠ 0.
- `CLOSED_WITHOUT_SELL` — `CLOSED` ואין SELL.

**⚠️ לא משווים מחירים / עלות ממוצעת:** `averageCost` של IB כולל עמלות ולכן לא יתאים לעולם.
**⚠️** פיצול מניה (split) יוצר פער קבוע — היומן צריך להיות במניות שאחרי הפיצול; "אישור התעלמות" (acknowledge) — מחוץ לתחום כרגע.

### 7.2 🟢 API + UI

**המימוש:** `warnings: [{ type, message }]` ב-`HoldingResponse` (הודעה באנגלית: "Trade log says 90 shares, IB reports 100 (difference 10)").
ב-UI: אייקון `TriangleAlert` ליד הסימבול עם `title`; ספירה בתת-הכותרת ("3 holdings need attention").
**⚠️** `NO_TRADES_LOGGED` בצבע עמום (slate) ו-`QUANTITY_MISMATCH` בענבר — כדי למנוע עייפות התראות בזמן ה-backfill.

### 7.3 🟢 בדיקות

תוספות ל-`HoldingHistoryTest`: כל אחד מ-3 סוגי האזהרות מופיע/לא מופיע; סבלנות עשרונית; `CLOSED` תקין (סך 0) — בלי אזהרה.

---

## סשן 8 — [רעיון עתידי] זיהוי שינוי → טיוטת עסקה

> **תנאי מוקדם:** סשן 6. **הרעיון:** בכל סנכרון משווים את הכמות ב-IB לכמות ששמורה ב-DB מהריצה הקודמת; פער = טיוטת עסקה שנשארת רק לאשר ולהשלים תאריך/מחיר/סיבה.

### 8.1 🟡 מיגרציה `V2__trade_draft.sql`

```sql
CREATE TABLE trade_draft (
    id                BIGSERIAL PRIMARY KEY,
    holding_id        BIGINT        NOT NULL REFERENCES holding (id),
    detected_on       DATE          NOT NULL,
    side              VARCHAR(4)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity          NUMERIC(20,6) NOT NULL CHECK (quantity > 0),
    previous_position NUMERIC(20,6) NOT NULL,
    new_position      NUMERIC(20,6) NOT NULL,
    status            VARCHAR(9)    NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'DISMISSED')),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
```
**⚠️** טבלה נפרדת ולא עמודת `is_draft` ב-`trade` — כך שהנגזרות (סשן 3) לעולם לא סופרות טיוטות בטעות.

### 8.2 🔴 לוגיקה ב-`PortfolioSyncService.sync`

**קוד קיים רלוונטי:** `refreshFromIb` (סשן 2.2) דורס את `position` — לפני הדריסה משווים.

**המימוש:**
- `position` חדש < שמור (מעבר לסבלנות) → טיוטת `SELL` בגודל ההפרש; גדול יותר → טיוטת `BUY`.
- החזקה שנעלמה (→ `CLOSED`) → טיוטת `SELL` בגודל ה-`position` הקודם.
- החזקה חדשה לגמרי (אין בסיס להשוואה) → **אין טיוטה** (מקבלת `NO_TRADES_LOGGED` מסשן 7).
- `detected_on` = תאריך הסנכרון.

**⚠️ Idempotent:** ה-`position` השמור מתעדכן בכל סנכרון, ולכן ריצה חוזרת ללא שינוי לא יוצרת טיוטה נוספת.
**⚠️ מגבלות:** כמה עסקאות בין שני סנכרונים מתמזגות לטיוטה נטו אחת; קנייה ומכירה שווות בגודל בין סנכרונים — בלתי נראות.
**⚠️ פעולות תאגיד והעברות** (פיצולים, ספין-אוף, ACAT) משנות כמות בלי עסקה → טיוטות שווא; לכן קיים "Dismiss".
**⚠️** התאריך המזוהה הוא תאריך הסנכרון ולא תאריך הביצוע האמיתי — הטופס מציג אותו כברירת מחדל שאפשר לערוך.

### 8.3 🟡 API

`drafts` (רק `PENDING`) ב-`HoldingResponse`: `{ id, detectedOn, side, quantity, previousPosition, newPosition }`.
`POST /api/trade-drafts/{id}/confirm` — גוף `TradeRequest` (יוצר `trade` ומסמן `CONFIRMED` **בטרנזקציה אחת**); `POST /api/trade-drafts/{id}/dismiss`.

### 8.4 🟡 UI

תג "1 detected change" בשורה; ב-`TradesPanel` בלוק מודגש: "Detected 2026-09-19: position 100 → 60 (sold 40)" עם "Record trade…" (פותח `TradeForm` ממולא: צד, כמות, תאריך = `detectedOn`, מחיר ריק) ו-"Dismiss".

### 8.5 🟢 בדיקות

טיוטת SELL כשה-position יורד; טיוטת BUY כשעולה; **אין** טיוטה בהחזקה חדשה; אין כשלא השתנה; סגירה → SELL של כל הכמות; confirm יוצר `trade` וסוגר את הטיוטה; dismiss סוגר בלי `trade`; ריצה חוזרת לא כופלת.

---

## סשן 9 — [רעיון עתידי] עבודה ללא TWS (Offline fallback)

> **תנאי מוקדם:** סשן 2 (בלתי-תלוי בסשנים 3–8). **הרעיון:** אם TWS סגור או לא מחובר — להציג את הנתונים השמורים האחרונים ולאפשר להמשיך להזין סקטור ועסקאות.
>
> **סטטוס (22.09.2026):** 9.1 בוצע מוקדם, בתוך סשן 2, ביוזמת עומרי — ראו שם. 9.2 ו-9.3 (דגל stale + באנר
> ב-UI) עדיין לא בוצעו ונשארים לסשן הזה, בכוונה (עומרי בחר להשאיר, 22.09.2026).

### ✅ 9.1 🟢 `Main` — במקום יציאה

**קוד קיים רלוונטי:** ה-runner (0.3): `if (snapshot == null) { System.exit(SpringApplication.exit(context)); }`.

**התיקון:** אם `snapshot == null` **וקיימת** שורה ב-`account_state` → `[db] TWS unavailable — serving the last saved portfolio (synced <asOf>)`, ממשיכים ל-`UiLauncher`.
אם אין נתונים שמורים בכלל — יוצאים כמו היום (אין מה להציג).

**בפועל (`TwsPortfolioRunner`, סשן 2):** ההודעה היא `[db] TWS unreachable; serving the portfolio from the
last sync, if any`, וה-runner **תמיד** ממשיך ל-`UiLauncher` — גם אם `account_state` ריק לגמרי — ונשען על
ה-503 הקיים ב-`PortfolioController` כדי לטפל במקרה הזה, במקום ענף נפרד שבודק אם יש נתונים ויוצא אם אין.
פשוט יותר מהתכנון המקורי, ואומת חי ב-22.09.2026.

### 9.2 🟢 מצב סנכרון ב-API

**המימוש:** bean `SyncStatus` (`SYNCED` / `STALE`) שה-runner קובע; `PortfolioResponse.stale: boolean`.

### 9.3 🟢 באנר ב-UI

באנר ענבר: "TWS was unavailable — showing data from {asOf}. Quantities and prices may be out of date." הטבלה ממשיכה להיות ניתנת לעריכה.

**⚠️ ב-fallback לא רץ סנכרון** — ולכן שום החזקה לא תסומן `CLOSED` בטעות בגלל הורדה כושלת.
**⚠️** זה לא "כפתור רענון": סנכרון מחדש בלי הפעלה מחדש של `run.sh` דורש ש-`IbGateway` יבקש שוב `reqAccountUpdates` — פריט נפרד שנדחה (מצוין ב-CLAUDE.md).

**בדיקת אימות סשן 9 (ידנית):** לסגור את TWS ולהריץ → ה-UI עולה עם הנתונים האחרונים ועם הבאנר; הזנת סקטור עובדת; עם DB ריק ובלי TWS → יציאה עם ההודעה הישנה.

---

## מחוץ לתחום (לא בתוכנית הזו)

- **Flex Query** כמקור אוטומטי לעסקאות (נדחה; שקוע במקום הזנה ידנית — עשוי לחזור כתוספת ל-backfill).
- **כפתור רענון** מ-TWS בלי הפעלה מחדש.
- **יצירה ידנית של החזקה** (למשל פוזיציה שנמכרה לפני הסנכרון הראשון).
- **Tax lots / FIFO**, ריבוי חשבונות ב-UI, היסטוריית NAV / TWR.
- **Thesis** (הליבה האמיתית של Milestone 1): מתחבר לאותה תשתית — `V?__thesis.sql` ואותו דפוס entity/endpoint/UI. תוכנית נפרדת.

---

## סדר עדיפויות מומלץ

| סשן | תוכן | אורך משוער | השפעה | סיכון | תנאי מוקדם |
|-----|------|-----------|--------|-------|------------|
| ✅ סשן 0 (0.1–0.6) | Maven + Spring Boot, אותה התנהגות *(נשאר: diff חי מול TWS)* | ~2.5 שעות | תשתית | 🔴 | אין |
| סשן 1 (1.1–1.5) | Postgres + Flyway + סכמה + ישויות + גיבוי | ~1.5 שעות | תשתית | 🟡 | סשן 0 |
| סשן 2 (2.1–2.5) | סנכרון בחיבור + ה-API מה-DB | ~2 שעות | נתונים | 🔴 | סשן 1 |
| סשן 3 (3.1–3.4) | נגזרות: תאריכים וזמן החזקה | ~1.5 שעות | לוגיקה | 🟡 | סשן 2 |
| סשן 4 (4.1–4.5) | endpoints כותבים + אבטחה | ~2 שעות | API | 🔴 | סשן 3 |
| סשן 5 (5.1–5.4) | UI: types, עמודות, מיון | ~1.5 שעות | UI | 🟡 | סשן 3 |
| סשן 6 (6.1–6.5) | UI: טפסי סקטור/עסקאות, סגורות | ~3 שעות | UI | 🟡 | סשנים 4+5 |
| 🔮 סשן 7 | אזהרות אי-התאמה | ~1.5 שעות | UI + לוגיקה | 🟡 | סשן 6 |
| 🔮 סשן 8 | זיהוי שינוי → טיוטת עסקה | ~2.5 שעות | לוגיקה + UI | 🔴 | סשן 6 |
| 🔮 סשן 9 | עבודה ללא TWS | ~1 שעה | חוויה | 🟢 | סשן 2 |
