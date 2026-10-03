# Investors — Session Implementation Plan

> **למי שקורא זאת בסשן חדש:** מדריך מימוש לפיצ'ר "כמה משקיעים בחשבון אחד": משקיע נוסף שהכסף שלו נמצא בחשבון ה-IB, שיוך
> מניות לכל משקיע (גם חלוקה של החזקה אחת — 39 NVDA: 15 של בעל החשבון, 24 של המשקיע השני), ותצוגה מהירה של המזומן שנשאר לכל
> אחד והרווח של כל אחד. נבנה **אחרי** סשנים 2–3 של [CLOSED_POSITIONS_TODO.md](CLOSED_POSITIONS_TODO.md) (עמלות ועסקאות סגורות
> ידניות): העמלה משפיעה על המזומן ועל העלות של כל משקיע, וטבלת העסקאות הסגורות הידניות מקבלת עמודת משקיע כבר מההתחלה.
> שייך לעקרון 6 ב-[TODO.md](TODO.md) (מדידה עצמית כנה): כל משקיע רואה כמה הרוויח על המניות שלו.
>
> סיכון לכל פריט: 🟢 נמוך | 🟡 בינוני | 🔴 גבוה. **✅ ליד מספר הפריט = בוצע.**

---

## החלטות שהתקבלו (03.10.2026, לא לפתוח מחדש)

1. **המשקיע הנוסף מוגדר במפורש, והמשקיע הראשי (בעל החשבון) הוא "כל השאר":** המזומן של בעל החשבון = המזומן ב-IB פחות המזומן
   של המשקיעים האחרים, ההון שלו = ה-NAV פחות ההון שלהם, והעלות של המניות שלו = העלות ב-IB פחות העלות שלהם. כך הסכום של כולם
   שווה תמיד בדיוק למה ש-IB מדווח, גם כשחסרות עסקאות. בקוד: משקיע = `investor`, המשקיע הראשי = `is_account_owner`. אין הגבלה
   לשניים — כל משקיע נוסף הוא עוד שורה.
2. **שיוך מניות = שדה משקיע על כל עסקה** (`trade.investor_id`, חובה). כל העסקאות שכבר הוזנו משויכות לבעל החשבון. ביצוע אחד
   ב-IB שמתחלק בין שניכם = שתי שורות באותו תאריך ובאותו מחיר. בכל החזקה: הכמות של משקיע נוסף = סכום העסקאות שלו; הכמות של בעל
   החשבון = הכמות של IB פחות הכמויות שלהם (החלטה 1). החלופה "כמות לכל משקיע על ההחזקה" נדחתה: בלי מחיר קנייה לכל משקיע אי
   אפשר לחשב לא מזומן ולא רווח.
3. **הכסף של משקיע נוסף = יומן הפקדות ומשיכות** (`investor_cash_movement`: תאריך, סוג `DEPOSIT` / `WITHDRAWAL`, סכום). הפקדה =
   כסף שהועבר לחשבון ה-IB; משיכה = כסף שיצא ממנו. "כמה מהכסף בתיק שייך לו" = כמה הוא הפקיד. לא מזינים "יש לו X מזומן" כמספר
   קבוע — המזומן משתנה בכל קנייה ומכירה, ולכן הוא נגזר. **לבעל החשבון אין יומן**: המזומן שלו מגיע מ-IB (החלטה 1).
4. **מזומן של משקיע נוסף = הפקדות − משיכות − קניות + מכירות − עמלות**, כולל העסקאות הסגורות הידניות שלו. נגזר בכל קריאה, לא
   נשמר (כמו `firstBuyDate`). ההפקדות משמשות **רק** למזומן — לא לרווח.
5. **רווח — מהמניות בלבד, אותה הגדרה לשני המשקיעים (עקביות):**
   - **רווח לא ממומש** = שווי המניות שלו היום (כמות × מחיר השוק של IB) − העלות שלהן. אחוז = רווח לא ממומש ÷ עלות, כמו העמודה
     של IB.
   - **רווח ממומש** = סך הרווח של העסקאות הסגורות שלו (החלטה 8, ולפי CLOSED_POSITIONS_TODO.md: מכירות − קניות − עמלות).
   - **סה"כ** = לא ממומש + ממומש (בדולרים, בלי אחוז).
   - **העלות של משקיע נוסף** = המחיר הממוצע של הקניות בסבב הנוכחי שלו (כולל עמלות) × הכמות שנשארה לו. מכירה חלקית לא משנה את
     הממוצע — כמו שיטת העלות הממוצעת של IB. **העלות של בעל החשבון** = העלות ב-IB (`position × averageCost`) פחות העלות של
     האחרים (החלטה 1).
   - **לא נכלל:** דיבידנדים, ריבית, ורווח ממכירה חלקית בתוך החזקה שעדיין פתוחה. אולי נרחיב בהמשך (ראה "מחוץ לתחום").
6. **מחיר חסר בעסקה של משקיע נוסף ← מה שתלוי בה `null` + אזהרה:** המזומן שלו (וגם של בעל החשבון, שנגזר ממנו), והעלות והרווח
   הלא ממומש באותה החזקה, אם הקנייה בסבב הנוכחי. לא מנחשים.
7. **דיבידנדים, ריבית ועמלות ש-IB גובה** נכנסים למזומן של החשבון ולא ליומן של אף משקיע — בגרסה הזו הם נזקפים למזומן של בעל
   החשבון (החלטה 1), ולא לרווח של אף אחד (החלטה 5).
8. **עסקאות סגורות לפי משקיע:** `HoldingHistory.of` רץ על העסקאות של כל משקיע בנפרד. אם בעל החשבון מכר את ה-15 שלו והמשקיע
   השני נשאר עם 24 — זו עסקה סגורה של בעל החשבון, למרות שההחזקה לא ירדה לאפס. התאריכים והאזהרות של ההחזקה עצמה
   (`firstBuyDate`, `QUANTITY_MISMATCH`…) ממשיכים להיות מחושבים מכל העסקאות יחד.
9. **אזהרות משקיע** (מוצגות, לא חוסמות — כמו בסשן 7 של HOLDING_DETAILS_TODO.md): עסקה בלי מחיר; מזומן שלילי; החזקה שבה העסקאות
   שלו מסתכמות ביותר מניות ממה ש-IB מדווח. בניגוד לאזהרות ההחזקה, יכולות להיות כמה בבת אחת.
10. **UI:** כרטיס סיכום לכל משקיע — מזומן, שווי מניות, הון (מזומן + מניות), רווח לא ממומש ($ ו-%), רווח ממומש, סה"כ; בכרטיס של
    משקיע נוסף גם סך ההפקדות נטו. החלוקה בעמודת הכמות: `39 (15 · 24)`. מסנן "הצג לפי משקיע" — לא בגרסה הזו.

## עקרונות קבועים

- כל עקרונות HOLDING_DETAILS_TODO.md בתוקף: read-only מול IB, `GET /api/portfolio` רק מתווסף, JSON בלבד בכתיבה, להסביר לפני
  עריכה. ה-endpoints החדשים כותבים רק נתונים שהוזנו ידנית (משקיעים והפקדות) — מותר לפי CLAUDE.md.
- **החישוב כתוב פעם אחת:** ב-`domain` (בלי Spring ובלי DB), עם בדיקות יחידה. הדוגמה למטה היא גם בדיקה.
- **USD בלבד:** כל ההחזקות היום ב-USD (נבדק ב-DB, 03.10.2026).
- שמות מחלקות ושדות בתוכנית הם הצעה — כל שם חדש מוסבר כשיוצרים אותו.
- Git / changelog / "עדכונים של סוף סשן" — רק ביוזמת המשתמש.

### דוגמה (משמשת גם כבדיקה)

לפי IB: NAV \u200F$100,000, מזומן \u200F$40,000, מניות בשווי \u200F$60,000 שעלו \u200F$50,000. NVDA ב-$180.
המשקיע השני הפקיד $30,000 וקנה 24 NVDA ב-$120 ($2,880). לבעל החשבון עסקה סגורה אחת (קנה ומכר AAPL) ברווח של $500.

|                  | המשקיע השני                     | בעל החשבון                                  |
|------------------|---------------------------------|---------------------------------------------|
| מזומן            | 30,000 − 2,880 = **$27,120**    | 40,000 − 27,120 = **$12,880**               |
| שווי מניות       | 24 × 180 = $4,320               | 60,000 − 4,320 = $55,680 (כולל 15 NVDA)     |
| הון              | 27,120 + 4,320 = $31,440        | 100,000 − 31,440 = $68,560                  |
| עלות המניות      | 24 × 120 = $2,880               | 50,000 − 2,880 = $47,120                    |
| רווח לא ממומש    | 4,320 − 2,880 = +$1,440 (+50%)  | 55,680 − 47,120 = +$8,560 (+18.2%)          |
| רווח ממומש       | $0                              | +$500                                       |
| **סה"כ רווח**    | **+$1,440**                     | **+$9,060**                                 |

---

## סשן 1 — סכמה, חישוב ו-API לקריאה

> **המטרה:** `GET /api/portfolio` מחזיר את המשקיעים עם המזומן, ההון והרווח של כל אחד, ואת החלוקה בכל החזקה. עדיין אין דרך
> להזין משקיע או הפקדה מה-API — הבדיקות מכניסות אותם ישירות ל-DB.

### 1.1 🟡 מיגרציה `V3__investors.sql`

V2 הוא של העסקאות הסגורות. אם הסדר ישתנה — המספר הפנוי הבא, ושורות `manual_closed_position` רק אם הטבלה כבר קיימת.
**לפני ההרצה הראשונה מול ה-DB האמיתי:** `scripts/backup-db.sh`.

```sql
CREATE TABLE investor (
    id                BIGSERIAL    PRIMARY KEY,
    name              VARCHAR(60)  NOT NULL UNIQUE,
    is_account_owner  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- At most one account owner: the investor who gets whatever the others' entries don't explain.
CREATE UNIQUE INDEX investor_one_account_owner ON investor (is_account_owner) WHERE is_account_owner;
INSERT INTO investor (name, is_account_owner) VALUES ('Me', TRUE);

-- Every trade belongs to an investor; the ones entered so far are the account owner's.
ALTER TABLE trade ADD COLUMN investor_id BIGINT REFERENCES investor (id);
UPDATE trade SET investor_id = (SELECT id FROM investor WHERE is_account_owner);
ALTER TABLE trade ALTER COLUMN investor_id SET NOT NULL;

ALTER TABLE manual_closed_position ADD COLUMN investor_id BIGINT REFERENCES investor (id);
UPDATE manual_closed_position SET investor_id = (SELECT id FROM investor WHERE is_account_owner);
ALTER TABLE manual_closed_position ALTER COLUMN investor_id SET NOT NULL;

-- Only for investors other than the account owner, whose cash comes from IB (checked by the API).
CREATE TABLE investor_cash_movement (
    id             BIGSERIAL     PRIMARY KEY,
    investor_id    BIGINT        NOT NULL REFERENCES investor (id),
    movement_date  DATE          NOT NULL,
    type           VARCHAR(10)   NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL')),
    amount         NUMERIC(20,6) NOT NULL CHECK (amount > 0),
    note           VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX investor_cash_movement_investor_date ON investor_cash_movement (investor_id, movement_date);
```

### 1.2 🟢 `db`: entities ו-repositories

- `InvestorEntity` (`id`, `name`, `accountOwner`), `InvestorCashMovementEntity`, `CashMovementType` (`DEPOSIT` / `WITHDRAWAL`),
  והאחסון שלהם (repositories).
- `TradeEntity` ו-`ManualClosedPositionEntity` מקבלים את המשקיע (חובה). `TradeEntity.toTradeFact()` מוסיף את ה-id שלו.

### 1.3 🟡 `domain`: החישוב

- `TradeFact` מקבל `investorId`.
- `HoldingHistory` מוסיף את העלות של המניות שנשארו בסבב הנוכחי (החלטה 5: ממוצע הקניות בסבב, כולל עמלות, × הכמות שנשארה;
  `null` אם לאחת הקניות בסבב אין מחיר).
- `record InvestorFigures(netDeposits, cash, sharesValue, costBasis, realizedPnl)` — הסכומים הגולמיים; נגזרים ממנו `equity()`,
  `unrealizedPnl()`, `unrealizedPnlPercent()`, `totalPnl()` (באותה רוח כמו `ClosedPosition`). רכיב `null` ← הנגזרים ממנו
  `null`. `netDeposits` הוא `null` לבעל החשבון (אין לו יומן).
- `InvestorSplit.of(...)` — מקבל לכל החזקה רשומה פשוטה בלי JPA (כמות, מחיר שוק, שווי שוק ועלות מ-IB, והעסקאות עם המשקיע של
  כל אחת), את יומני ההפקדות, את המזומן וה-NAV מ-IB ואת ה-id של בעל החשבון. מחזיר לכל משקיע `InvestorFigures` ואזהרות
  (`InvestorWarning(type, message)` + `InvestorWarningType`), ולכל החזקה את הכמות של כל משקיע.
  - משקיע נוסף: מזומן לפי החלטה 4; שווי מניות = הכמות שלו × מחיר השוק של IB, בכל החזקה; עלות ורווח ממומש — מ-`HoldingHistory`
    של העסקאות שלו.
  - בעל החשבון: מזומן, שווי מניות, הון ועלות — של IB פחות של האחרים (החלטה 1); רווח ממומש — מהעסקאות הסגורות שלו.
  - ⚠️ החזקה `CLOSED`: הסנכרון מאפס בה את הכמות ואת שווי השוק, אבל מחיר השוק נשאר האחרון שנראה. משקיע נוסף שהעסקאות שלו עדיין
    מראות מניות שם נספר לפי המחיר הזה, ומקבל את אזהרת "יותר מניות ממה ש-IB מדווח" — שאומרת להזין את המכירה. הסכום מול IB
    נשמר (בעל החשבון מקבל את ההפרש).
- `HoldingEntity`: היסטוריית העסקאות לכל משקיע בנפרד, ו-`PortfolioResponse.closedPositionsOf` לוקח ממנה את העסקאות הסגורות
  (החלטה 8).

### 1.4 🟢 API (קריאה)

- `PortfolioResponse.investors` (בסוף): `InvestorResponse(id, name, accountOwner, netDeposits, cash, sharesValue, equity,
  costBasis, unrealizedPnl, unrealizedPnlPercent, realizedPnl, totalPnl, cashMovements, warnings)`, ו-`CashMovementResponse(id,
  movementDate, type, amount, note)`.
- `HoldingResponse.investorQuantities` (בסוף): `InvestorQuantityResponse(investorId, quantity)` — רק משקיעים שהכמות שלהם בהחזקה
  אינה 0.
- `TradeResponse.investorId` ו-`ClosedPositionResponse.investorId` (בסוף).
- `PortfolioReadService` טוען גם את המשקיעים ואת היומנים שלהם, בלי שאילתה נוספת לכל משקיע.

### 1.5 🟢 בדיקות

- `domain`: הדוגמה למעלה; משיכה; עמלה בעלות ובמזומן; מכירה חלקית לא משנה את העלות הממוצעת; עסקה בלי מחיר; מזומן שלילי; יותר
  מניות מ-IB; עסקה סגורה של משקיע אחד בתוך החזקה פתוחה.
- `PortfolioReadServiceTest`: מה-DB ועד ה-response, כולל שעסקה קיימת שייכת לבעל החשבון אחרי המיגרציה.
- `PortfolioControllerTest`: שמות השדות ב-JSON.

**בדיקת אימות סשן 1:** `mvn -q test` ירוק; `./run.sh 7599` עולה (המיגרציה עוברת ו-`ddl-auto=validate` מקבל את ה-entities),
ו-`/api/portfolio` מחזיר את בעל החשבון עם המזומן, ההון והרווח של כל החשבון.

---

## סשן 2 — backend: הזנת משקיעים, הפקדות, ומשקיע לעסקה

### 2.1 🟡 משקיעים והפקדות

`InvestorWriteController` + `InvestorWriteService`, באותה תבנית כמו `HoldingWriteController` / `HoldingWriteService` — JSON בלבד,
`@Valid`, id לא קיים ← 404:

- `POST /api/investors` (201) ו-`PUT /api/investors/{investorId}` (204, שינוי שם) — `InvestorRequest(name)`. שם תפוס ← 409.
  בעל החשבון נוצר רק במיגרציה; ה-API לא יוצר בעל חשבון נוסף.
- `POST /api/investors/{investorId}/cash-movements` (201), `PUT /api/cash-movements/{movementId}` (200),
  `DELETE /api/cash-movements/{movementId}` (204) — `CashMovementRequest(movementDate, type, amount, note)`, עם אותן מגבלות כמו
  `TradeRequest` (`@PastOrPresent`, `@Positive`, `@Digits(integer = 14, fraction = 6)`, `@Size(max = 500)`). הפקדה לבעל החשבון
  ← 400 (המזומן שלו מגיע מ-IB).

### 2.2 🟢 משקיע לעסקה

- `TradeRequest.investorId` — אופציונלי; `null` = בעל החשבון (ברירת המחדל, וכך ה-UI הנוכחי ממשיך לעבוד עד סשן 3). id שלא
  קיים ← 400.
- אותו דבר ב-request של העסקה הסגורה הידנית.

### 2.3 🟢 בדיקות

Controller (קודי סטטוס, ולידציה, JSON בלבד, 409 על שם תפוס, 400 על הפקדה לבעל החשבון) ו-Service (מה נשמר), באותה חלוקה כמו
`HoldingWriteControllerTest` / `HoldingWriteServiceTest`.

**בדיקת אימות סשן 2:** `mvn -q test` ירוק; ב-curl מוסיפים משקיע, הפקדה ועסקה שלו — והמספרים ב-`/api/portfolio` כמו בדוגמה.

---

## סשן 3 — UI

- `types/portfolio.ts`: `Investor`, `CashMovement`, `investors`, `investorQuantities`, `investorId`.
- `components/InvestorsSummary.tsx`: כרטיס לכל משקיע מתחת ל-`SummaryBar` (החלטה 10), עם האזהרות שלו. מוצג כשיש יותר ממשקיע
  אחד; אחרת רק קישור קטן "+ Add investor".
- בכרטיס של משקיע נוסף: כפתור "Deposits" שפותח את היומן (רשימה, הוספה, עריכה, מחיקה), באותו סגנון כמו `TradesPanel` /
  `TradeForm`.
- `PositionsTable`: בעמודת הכמות `39 (15 · 24)` כשמשקיע נוסף מחזיק חלק, עם tooltip של השמות.
- `TradeForm`: בחירת משקיע (ברירת מחדל: בעל החשבון) כשיש יותר ממשקיע אחד; `TradesPanel` ו-`ClosedPositionsTable`: עמודת משקיע.
- `apiClient.ts`: `addInvestor`, `renameInvestor`, `addCashMovement`, `changeCashMovement`, `deleteCashMovement` — בשמות של
  ה-service, כמו `addTrade` / `changeTrade` / `deleteTrade`.

**בדיקת אימות סשן 3:** `npm run build` ירוק; בדפדפן — מזינים את הדוגמה ורואים את המספרים בכרטיסים ואת `39 (15 · 24)` בטבלה.

---

## הערות שימוש

- **הגדרה ראשונית של משקיע שכבר מחזיק מניות:** ההפקדות שלו + העסקאות שלו, עם מחיר. מי שלא זוכר כל עסקה — מספיקה קנייה אחת
  בכמות שלו ובמחיר הממוצע שלו: המזומן והרווח הלא ממומש יוצאים נכון, כי הם תלויים רק בסך העלות.
- **העברת מניות ביניכם** (בלי עסקה ב-IB): מכירה של אחד + קנייה של השני, באותו תאריך ובאותו מחיר. הכמות הכוללת לא משתנה,
  והמזומן עובר ביניכם.
- **העברת כסף ביניכם בתוך החשבון:** ממך אליו = הפקדה ביומן שלו; ממנו אליך = משיכה מהיומן שלו. המזומן שלך מתעדכן מעצמו
  (החלטה 1).

## מחוץ לתחום (כרגע)

- **רווח כולל מול הכסף שהופקד** (הון − הפקדות נטו), שכולל גם דיבידנדים, ריבית ומכירות חלקיות — דורש יומן הפקדות גם לבעל
  החשבון. הטבלה `investor_cash_movement` כבר מתאימה לזה; כשנרחיב, מורידים את ה-400 של 2.1.
- רווח ממומש ממכירה חלקית בתוך החזקה פתוחה (FIFO / tax lots).
- דיבידנדים, ריבית ועמלות IB לפי משקיע.
- מסנן "הצג לפי משקיע" בטבלת ה-Positions.
- תשואה משוקללת זמן (TWR) לכל משקיע.
- מטבע שאינו USD, כמה חשבונות IB, מחיקת משקיע.
