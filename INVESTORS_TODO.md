# Investors — Session Implementation Plan

> **למי שקורא זאת בסשן חדש:** מדריך מימוש לפיצ'ר "כמה משקיעים בחשבון אחד": משקיע נוסף שהכסף שלו נמצא בחשבון ה-IB, שיוך
> מניות לכל משקיע (גם חלוקה של החזקה אחת — 39 NVDA: 15 של בעל החשבון, 24 של המשקיע השני), ותצוגה מהירה של המזומן שנשאר לכל
> אחד והרווח של כל אחד. נבנה **אחרי** [CLOSED_POSITIONS_TODO.md](CLOSED_POSITIONS_TODO.md) (כל חמשת הסשנים): העמלה משפיעה על
> המזומן ועל העלות של כל משקיע, והעסקאות של פוזיציה ידנית הן שורות ב-`trade` — ולכן מקבלות משקיע כמו כל עסקה.
>
> **עודכן 03.10.2026, לפני סשן 1:** התוכנית נכתבה לפני העיצוב מחדש של העסקאות הסגורות (V3, עלות ממוצעת, מכירה חלקית, פוזיציות
> ידניות עם עסקאות). מה שהשתנה בגלל זה: החלטה 5 (מכירה חלקית נכללת), החלטות 11–12 החדשות, 1.1–1.4 ו"מחוץ לתחום".
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
   - **רווח ממומש** = סך הרווח של העסקאות הסגורות שלו (החלטה 8, ולפי CLOSED_POSITIONS_TODO.md: מכירות − עלות ממוצעת − עמלות),
     **כולל מכירה חלקית**: מאז החלטות 9–10 שם, כל סבב שיש בו מכירה הוא עסקה סגורה. לפי מטבע (החלטה 11).
   - **סה"כ** = לא ממומש + ממומש בדולרים (בלי אחוז).
   - **העלות של משקיע נוסף** = העלות הממוצעת של המניות שנשארו לו בסבב הנוכחי (כולל עמלות הקנייה) — אותו חישוב כמו של העסקאות
     הסגורות. מכירה חלקית לא משנה את הממוצע — כמו שיטת העלות הממוצעת של IB. דוגמה: קנה 24 NVDA ב-$120 ומכר 4 ב-$180 ← רווח
     ממומש +$240, ו-20 המניות שנשארו עולות $2,400. **העלות של בעל החשבון** = העלות ב-IB (`position × averageCost`) פחות העלות של
     האחרים (החלטה 1).
   - **לא נכלל:** דיבידנדים וריבית. אולי נרחיב בהמשך (ראה "מחוץ לתחום").
6. **מחיר חסר בעסקה של משקיע נוסף ← מה שתלוי בה `null` + אזהרה:** המזומן שלו (וגם של בעל החשבון, שנגזר ממנו), והעלות והרווח
   הלא ממומש באותה החזקה, אם הקנייה בסבב הנוכחי. לא מנחשים.
7. **דיבידנדים, ריבית ועמלות ש-IB גובה** נכנסים למזומן של החשבון ולא ליומן של אף משקיע — בגרסה הזו הם נזקפים למזומן של בעל
   החשבון (החלטה 1), ולא לרווח של אף אחד (החלטה 5).
8. **עסקאות סגורות לפי משקיע:** `HoldingHistory.of` רץ על העסקאות של כל משקיע בנפרד — בהחזקה ובפוזיציה ידנית. אם בעל החשבון מכר את ה-15 שלו והמשקיע
   השני נשאר עם 24 — זו עסקה סגורה של בעל החשבון, למרות שההחזקה לא ירדה לאפס. התאריכים והאזהרות של ההחזקה עצמה
   (`firstBuyDate`, `QUANTITY_MISMATCH`…) ממשיכים להיות מחושבים מכל העסקאות יחד.
9. **אזהרות משקיע** (מוצגות, לא חוסמות — כמו בסשן 7 של HOLDING_DETAILS_TODO.md): עסקה בלי מחיר; מזומן שלילי; החזקה שבה העסקאות
   שלו מסתכמות ביותר מניות ממה ש-IB מדווח. בניגוד לאזהרות ההחזקה, יכולות להיות כמה בבת אחת.
10. **UI:** כרטיס סיכום לכל משקיע — מזומן, שווי מניות, הון (מזומן + מניות), רווח לא ממומש ($ ו-%), רווח ממומש, סה"כ; בכרטיס של
    משקיע נוסף גם סך ההפקדות נטו. החלוקה בעמודת הכמות: `39 (15 · 24)`. מסנן "הצג לפי משקיע" — לא בגרסה הזו.
11. **מטבע** (עומרי, 03.10.2026): הרווח הממומש בכרטיס — **לפי מטבע** (`+500 USD · +950 HKD`), כמו הסכומים בטבלת העסקאות
    הסגורות; היום רק פוזיציה ידנית אחת, 9988.HK, ב-HKD. המזומן, שווי המניות, ההון, העלות והסה"כ — בדולרים בלבד: עסקה של משקיע נוסף
    במטבע אחר לא נספרת במזומן שלו, ומקבלת אזהרה.
12. **פרטים שנקבעו לפני הבנייה (03.10.2026):**
    - ההון של בעל החשבון = ה-NAV פחות ההון של האחרים (החלטה 1), ולכן הוא לא בדיוק מזומן + מניות: ב-DB האמיתי ה-NAV גבוה ב-$15.72
      ממזומן + שווי המניות (כנראה דיבידנד או ריבית שנצברו — של בעל החשבון, החלטה 7).
    - עסקה סגורה בלי רווח (מחיר חסר או מכירת יתר) לא נספרת ברווח הממומש, ואזהרה אומרת כמה כאלה יש — כמו "(n without prices)" בטבלת
      העסקאות הסגורות. בלי זה עסקה אחת בלי מחיר הייתה מוחקת את כל הרווח הממומש.
    - `trade.investor_id` חובה, ולכן כבר בסשן 1 העסקאות החדשות מה-UI של היום (וגם פוזיציה ידנית חדשה) משויכות לבעל החשבון.

## עקרונות קבועים

- כל עקרונות HOLDING_DETAILS_TODO.md בתוקף: read-only מול IB, `GET /api/portfolio` רק מתווסף, JSON בלבד בכתיבה, להסביר לפני
  עריכה. ה-endpoints החדשים כותבים רק נתונים שהוזנו ידנית (משקיעים והפקדות) — מותר לפי CLAUDE.md.
- **החישוב כתוב פעם אחת:** ב-`domain` (בלי Spring ובלי DB), עם בדיקות יחידה. הדוגמה למטה היא גם בדיקה.
- **USD:** כל ההחזקות היום ב-USD (נבדק ב-DB, 03.10.2026); פוזיציה ידנית אחת (9988.HK) ב-HKD — ראה החלטה 11.
- שמות מחלקות ושדות בתוכנית הם הצעה — כל שם חדש מוסבר כשיוצרים אותו.
- Git / changelog / "עדכונים של סוף סשן" — רק ביוזמת המשתמש.

### דוגמה (משמשת גם כבדיקה)

לפי IB: NAV ‏$100,000, מזומן ‏$40,000, מניות בשווי ‏$60,000 שעלו ‏$50,000. NVDA ב-$180.
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
>
> **סטטוס (04.10.2026):** 1.1–1.5 נבנו על `maven-spring-boot`, עדיין לא committed. `mvn -q test` ירוק — 161 בדיקות (29 חדשות:
> `InvestorSummaryCalculatorTest` 13, `PositionTradesTest` 7, `heldCost` 7 ב-`HoldingHistoryTest`, ואחת ב-`PortfolioControllerTest` וב-
> `PortfolioReadServiceTest`). V4 רצה על `portfolioboss_test`, ונבדקה על עותק זמני של ה-DB האמיתי (38 העסקאות שויכו ל-"Me"; העותק נמחק).
> ✅ גיבוי (`~/PortfolioBossBackups/portfolioboss-2026-10-04.sql.gz`, שוחזר ל-DB זמני וכל הטבלאות זהות למקור — שורות ותוכן), ואז
> `./run.sh 7599` מול `portfolioboss`: V4 רצה, ו-`/api/portfolio` מחזיר את "Me" עם המספרים של IB בדיוק (מזומן 11,486.74, ערך כולל
> 69,554.49 = ה-NAV), רווח ממומש USD ‏+19,180.45 ו-HKD ‏+950 — אותו סכום כמו טבלת העסקאות הסגורות.
> שינויים מהתוכנית: השמות (החלטה 12 ו-1.3); `Utils.decimalOrNull` (מספר של IB ל-`BigDecimal`, `null` כש-IB לא דיווח); `getTradeFacts()`
> ב-`HoldingEntity` / `ManualPositionEntity` (השם — עומרי); `ManualPositionEntity.tradeHistory()` נמחק — העסקאות הסגורות עוברות עכשיו
> דרך `toPositionTrades()`, לכל משקיע; `InvestorRepository.accountOwner()` לשני ה-services של הכתיבה.

### 1.1 🟡 מיגרציה `V4__investors.sql`

V1–V3 כבר רצו (V3 = פוזיציות ידניות, שמחקה את `manual_closed_position`). העסקאות של פוזיציה ידנית הן שורות ב-`trade`, ולכן
`trade.investor_id` מכסה גם אותן — אין עמודת משקיע על `manual_position`.
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

- `InvestorEntity` (`id`, `name`, `accountOwner`, `cashMovements`), `InvestorCashMovementEntity`, `CashMovementType` (`DEPOSIT` /
  `WITHDRAWAL`), ו-`InvestorRepository` (המשקיעים עם היומנים שלהם באותה שאילתה). ה-repository של ההפקדות — בסשן 2, עם הכתיבה.
- `TradeEntity.investorId` (חובה) — מספר ולא קישור ל-entity: העסקה לא צריכה את השם של המשקיע (ה-UI מקבל אותו מ-`investors`),
  וקישור היה עולה בשאילתה נוספת. `TradeEntity.toTradeFact()` מעביר אותו.
- `HoldingWriteService` ו-`ManualPositionWriteService` משייכים כל עסקה חדשה לבעל החשבון (החלטה 12); בחירת משקיע — סשן 2.

### 1.3 🟡 `domain`: החישוב

- `TradeFact` מקבל `investorId`, ו-`cashFlow()`: מה שהעסקה עשתה למזומן (קנייה: −כמות × מחיר − עמלה; מכירה: +כמות × מחיר −
  עמלה; `null` בלי מחיר).
- `HoldingHistory.heldCost` — העלות של המניות שנשארו בסבב הנוכחי (החלטה 5), מאותו `AverageCostCalculator` של העסקאות הסגורות;
  `null` אם לאחת הקניות שעוד מוחזקות אין מחיר.
- `CashMovementFact(investorId, type, amount)` — הפקדה או משיכה, מצומצמת למה שהחישוב צריך (כמו `TradeFact`).
- `PositionTrades(symbol, currency, ibHolding, trades)` — החזקה (עם המספרים של IB) או פוזיציה ידנית (`ibHolding` = `null`) עם כל
  העסקאות שלה, בלי JPA; `HoldingEntity` ו-`ManualPositionEntity` בונים אותה. נותנת לכל משקיע את העסקאות, ה-`HoldingHistory` (החלטה 8)
  והכמות שלו — ולבעל החשבון: הכמות של IB פחות של האחרים.
- `record InvestorSummary(depositsMinusWithdrawals, cash, sharesValue, totalValue, sharesCost, realizedPnlByCurrency, warnings)` —
  המספרים של כרטיס הסיכום (החלטה 10), הסכומים הגולמיים; נגזרים ממנו `unrealizedPnl()`, `unrealizedPnlPercent()`, `totalPnl()` (באותה
  רוח כמו `ClosedPosition`). רכיב `null` ← הנגזרים ממנו `null`. `depositsMinusWithdrawals` הוא `null` לבעל החשבון (אין לו יומן).
  `totalValue` (ההון: מזומן + מניות) הוא רכיב ולא נגזר: של בעל החשבון מגיע מה-NAV (החלטה 12). השמות נבחרו עם עומרי (04.10.2026)
  במקום `InvestorFigures` / `netDeposits` / `equity` / `costBasis`.
- `InvestorSummaryCalculator` (במקום `InvestorSplit`) — מקבל את ה-`PositionTrades` של כל ההחזקות והפוזיציות הידניות, את ההפקדות,
  את המשקיעים ואת המזומן וה-NAV מ-IB, ומחזיר `InvestorSummary` לכל משקיע.
  - משקיע נוסף: מזומן לפי החלטה 4; שווי מניות = הכמות שלו × מחיר השוק של IB, בכל החזקה; עלות ורווח ממומש — מ-`HoldingHistory`
    של העסקאות שלו.
  - בעל החשבון: מזומן, שווי מניות, הון ועלות — של IB פחות של האחרים (החלטה 1); רווח ממומש — מהעסקאות הסגורות שלו.
  - אזהרות (`InvestorWarning(type, message)` + `InvestorWarningType`): עסקה בלי מחיר, מזומן שלילי, יותר מניות ממה ש-IB מדווח
    (החלטה 9), עסקה שלא בדולר (החלטה 11), עסקאות סגורות שלא נספרו (החלטה 12).
  - ⚠️ החזקה `CLOSED`: הסנכרון מאפס בה את הכמות ואת שווי השוק, אבל מחיר השוק נשאר האחרון שנראה. משקיע נוסף שהעסקאות שלו עדיין
    מראות מניות שם נספר לפי המחיר הזה, ומקבל את אזהרת "יותר מניות ממה ש-IB מדווח" — שאומרת להזין את המכירה. הסכום מול IB
    נשמר (בעל החשבון מקבל את ההפרש).
- `PortfolioResponse.closedPositionsOf` לוקח את העסקאות הסגורות מההיסטוריה של כל משקיע בנפרד (החלטה 8), בהחזקות ובפוזיציות
  הידניות.

### 1.4 🟢 API (קריאה)

- `PortfolioResponse.investors` (בסוף): `InvestorResponse(id, name, accountOwner, depositsMinusWithdrawals, cash, sharesValue,
  totalValue, sharesCost, unrealizedPnl, unrealizedPnlPercent, realizedPnlByCurrency, totalPnl, cashMovements, warnings)` —
  `realizedPnlByCurrency` הוא `{"HKD": 950, "USD": 500}` (החלטה 11) — ו-`CashMovementResponse(id, movementDate, type, amount, note)`.
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

> **סטטוס (04.10.2026):** 2.1–2.3 נבנו על `maven-spring-boot`, עדיין לא committed. `mvn -q clean test` ירוק — 193 בדיקות (32 חדשות:
> `InvestorWriteControllerTest` 14, `InvestorWriteServiceTest` 12, ארבע ב-`HoldingWriteServiceTest` ושתיים ב-`ManualPositionWriteServiceTest`).
> בדיקת curl — על **עותק זמני** של ה-DB האמיתי (האפליקציה עם `SPRING_DATASOURCE_URL`, בלי לשנות קובץ), כדי לא להשאיר "Avi" מזויף ב-DB
> האמיתי (אין עדיין מחיקת משקיע): Avi + הפקדה 30,000 + 24 NVDA ב-120 ← מזומן 27,120, עלות 2,880, ‏NVDA ‏`31 → 7 · 24`, וסכום המזומן
> וסכום הערך הכולל של שניהם = של IB בדיוק. 409 / 400 / 415 כמצופה; עריכה בלי `investorId` השאירה את העסקה של Avi. העותק נמחק.
> שמות שלא היו בתוכנית: `AddedInvestorResponse` (התשובה ל-POST; `InvestorResponse` תפוס על ידי הכרטיס), `InvestorCashMovementRepository`,
> `InvestorEntity.changeName`, `InvestorCashMovementEntity.changeDetails`, `TradeEntity.changeInvestor`, `InvestorWriteService.investorOfTrade`.
> שם תפוס נבדק בלי הבדל בין אותיות גדולות לקטנות ("avi" = "Avi").

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

- `TradeRequest.investorId` — אופציונלי. **ביצירת עסקה** `null` = בעל החשבון (ברירת המחדל, וכך ה-UI הנוכחי ממשיך לעבוד עד סשן 3);
  **בעריכה** (`PUT /api/trades/{id}`) `null` = העסקה נשארת של המשקיע שהיה לה, ומשקיע משתנה רק כששולחים אותו (עומרי, 04.10.2026).
  id שלא קיים ← 400. הבדיקה במקום אחד: `InvestorWriteService.investorOfTrade`; `TradeEntity.changeInvestor` נפרד מ-`changeDetails`.
- אותו דבר ב-`NewManualPositionRequest` (הקנייה והמכירה הראשונות של פוזיציה ידנית).

### 2.3 🟢 בדיקות

Controller (קודי סטטוס, ולידציה, JSON בלבד, 409 על שם תפוס, 400 על הפקדה לבעל החשבון) ו-Service (מה נשמר), באותה חלוקה כמו
`HoldingWriteControllerTest` / `HoldingWriteServiceTest`.

**בדיקת אימות סשן 2:** `mvn -q test` ירוק; ב-curl מוסיפים משקיע, הפקדה ועסקה שלו — והמספרים ב-`/api/portfolio` כמו בדוגמה.

---

## סשן 3 — UI

> **סטטוס (04.10.2026):** נבנה על `maven-spring-boot`, עדיין לא committed. `npm run build` ירוק; רינדור בצד השרת (בלי דפדפן) של הכרטיסים,
> הטבלאות והטפסים עם הדוגמה — 13 בדיקות עברו. ✅ בדיקה בדפדפן — עומרי (04.10.2026): הכול תקין.
> קבצים חדשים: `InvestorsSummary.tsx` (האזור, הכרטיסים, הוספה ושינוי שם), `CashMovementsPanel.tsx` (יומן ההפקדות: רשימה + טופס),
> `InvestorsContext.tsx` (רשימת המשקיעים לכל רכיב בלי להעביר אותה דרך כל הרמות — React context — ועזרים: `accountOwnerOf`,
> `investorNameOf`, `hasSeveralInvestors`), `InvestorSelect.tsx` (שדה "Investor" בטופס העסקה ובטופס הפוזיציה הידנית).
> בדרך: המפתח של שורה בטבלת העסקאות הסגורות (ושל השורה הפתוחה) כולל עכשיו את המשקיע — אחרת שני משקיעים שקנו אותה מניה באותו יום
> קיבלו אותו מפתח. טופס העסקה שולח תמיד את המשקיע שנבחר (ברירת מחדל: בעל החשבון; בעריכה — של העסקה).

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

## סשן 4 — הנתח של כל משקיע ברווח של פוזיציה משותפת

> **המטרה** (עומרי, 04.10.2026 — היה "פתוח — להמשך"): בפוזיציה שמחולקת בין כמה משקיעים, לראות במקום אחד את הנתח של כל אחד ברווח —
> **לא ממומש** (לכל החזקה, לא רק הסך על פני כל ההחזקות שבכרטיס) ו**ממומש** (הסך של הפוזיציה והחלוקה, למשל "NVDA ‏+1,000: Me ‏+400 ·
> Avi ‏+600"), וגם את הרווח הממומש שמעל טבלת העסקאות הסגורות מחולק לפי משקיע. בלי מיגרציה ובלי endpoint חדש.
>
> **סטטוס (04.10.2026):** 4.1–4.4 נבנו על `maven-spring-boot`, עדיין לא committed. `mvn -q clean test` ירוק — 197 בדיקות (4 חדשות: שלוש
> ב-`PositionTradesTest`, אחת ב-`InvestorSummaryCalculatorTest`; ובדיקות קיימות ב-`PortfolioControllerTest` וב-`PortfolioReadServiceTest`
> הורחבו). `npm run build` ירוק; רינדור בצד השרת (בלי דפדפן) של הטבלה ושל שורת הסכומים — 16 בדיקות עברו.
> ✅ **06.10.2026:** `mvn -q clean test` (197) ו-`npm run build` ירוקים שוב. על עותק זמני של ה-DB (גיבוי
> `portfolioboss-2026-10-06.sql.gz`, שוחזר וזהה למקור; העותק נמחק אחרי הבדיקה): בכל 7 ההחזקות החלקים מסתכמים בשורה של IB, והחלקים של כל
> משקיע = הכרטיס שלו (TTWO משותפת באמת: Me 15 · Shay 38). לבדיקת הפוזיציה הידנית נוספו לעותק קנייה ומכירה של Shay ב-NVDA הידנית
> (Me ‏+91.87 · Shay ‏+23.40). בדיקה בדפדפן — עומרי: הכול תקין.

### החלטות (04.10.2026)

13. **אותו חישוב כמו בכרטיסים:** בכל החזקה — למשקיע נוסף שווי = הכמות שלו × מחיר השוק של IB, ועלות = `heldCost` שלו; לבעל החשבון השווי
    והעלות של IB פחות של האחרים (החלטה 1). `InvestorSummaryCalculator` סוכם את אותן מתודות של `PositionTrades`, ולכן הסכום על פני כל
    ההחזקות = הכרטיס. במטבע של ההחזקה (היום כולן ב-USD).
14. **הרווח הממומש לפי פוזיציה נסכם ב-UI**, מ-`closedPositions` — יש בהן כבר `holdingId` / `manualPositionId`, `investorId` ו-`realizedPnl` (כמו
    `RealizedPnlTotals`). אם לאחת העסקאות הסגורות של משקיע בפוזיציה אין רווח (מחיר חסר או מכירת יתר), הרווח הממומש שלו שם `—` — לא סכום
    חלקי.
15. **שורת הסה"כ** בטבלת החלוקה = המספרים של IB להחזקה כולה, כמו השורה בטבלת ה-Positions. ב-DB האמיתי הרווח הלא ממומש של IB = שווי −
    כמות × עלות ממוצעת, בהפרש של פחות מסנט (נבדק 04.10.2026), ולכן החלקים מסתכמים בה.
16. **פיצול בעמודת ה-Unrealized P&L של הטבלה** (`+2,190 (+750 · +1,440)`) — לא בסשן הזה: הטבלה כבר רחבה (11 עמודות), וסכום כספי מפוצל
    ארוך. ראה "מחוץ לתחום".

### 4.1 🟢 `domain`: החלק של כל משקיע בהחזקה

- `PositionTrades.sharesValueOf(investorId)` ו-`sharesCostOf(investorId)` — עוברות לשם מ-`InvestorSummaryCalculator`, שקורא להן.
- `InvestorPart(quantity, sharesValue, sharesCost)` — החלק של משקיע אחד בהחזקה אחת, עם `unrealizedPnl()` ו-`unrealizedPnlPercent()` נגזרים
  (כמו ב-`InvestorSummary`). `PositionTrades.partsByInvestor(accountOwnerId)` — לאותם משקיעים כמו `quantitiesByInvestor` (רק מי שמחזיק משהו).

### 4.2 🟢 API (קריאה)

- `InvestorQuantityResponse` מקבל בסוף `sharesValue`, `sharesCost`, `unrealizedPnl`, `unrealizedPnlPercent`. השם של הרשימה ב-JSON
  (`investorQuantities`) נשאר.

### 4.3 🟡 UI

- `InvestorSplitTable.tsx`: טבלה "By investor" בשורה הנפתחת של החזקה (Positions) ושל פוזיציה ידנית (העסקאות הסגורות) — Investor, Qty, Cost,
  Value, Unrealized, %, Realized, ושורת סה"כ. בפוזיציה ידנית רק Investor ו-Realized (IB לא מחזיק בה כלום). מוצגת רק כשלמשקיע שאינו בעל
  החשבון יש עסקה בפוזיציה.
- `RealizedPnlTotals`: שורה נוספת לפי משקיע (`Me +18,000.00 USD · Avi +1,180.00 USD`), כשיש יותר ממשקיע אחד.

### 4.4 🟢 בדיקות

- `PositionTradesTest`: הדוגמה (15 + 24 NVDA) — השווי, העלות והרווח של כל אחד; בעל החשבון = של IB פחות של האחרים; מחיר חסר.
- `InvestorSummaryCalculatorTest`: סכום החלקים של כל משקיע בכל ההחזקות = הכרטיס שלו.
- `PortfolioControllerTest` (שמות השדות ב-JSON) ו-`PortfolioReadServiceTest` (הדוגמה מה-DB).

**בדיקת אימות סשן 4:** `mvn -q clean test` ו-`npm run build` ירוקים; בדפדפן — על עותק זמני של ה-DB (הוספת משקיע נשארת, אין מחיקה): הטבלה
בשורה הנפתחת של NVDA מסתכמת בשורה של IB, והחלקים של כל משקיע על פני ההחזקות = הכרטיס.

---

## הערות שימוש

- **הגדרה ראשונית של משקיע שכבר מחזיק מניות:** ההפקדות שלו + העסקאות שלו, עם מחיר. מי שלא זוכר כל עסקה — מספיקה קנייה אחת
  בכמות שלו ובמחיר הממוצע שלו: המזומן והרווח הלא ממומש יוצאים נכון, כי הם תלויים רק בסך העלות.
- **העברת מניות ביניכם** (בלי עסקה ב-IB): מכירה של אחד + קנייה של השני, באותו תאריך ובאותו מחיר. הכמות הכוללת לא משתנה,
  והמזומן עובר ביניכם.
- **העברת כסף ביניכם בתוך החשבון:** ממך אליו = הפקדה ביומן שלו; ממנו אליך = משיכה מהיומן שלו. המזומן שלך מתעדכן מעצמו
  (החלטה 1).

## מחוץ לתחום (כרגע)

- פיצול הרווח הלא ממומש בעמודה של טבלת ה-Positions (`+2,190 (+750 · +1,440)`, החלטה 16) — היום בטבלה שבשורה הנפתחת.
- **רווח כולל מול הכסף שהופקד** (הון − הפקדות נטו), שכולל גם דיבידנדים, ריבית ומכירות חלקיות — דורש יומן הפקדות גם לבעל
  החשבון. הטבלה `investor_cash_movement` כבר מתאימה לזה; כשנרחיב, מורידים את ה-400 של 2.1.
- דיבידנדים, ריבית ועמלות IB לפי משקיע.
- מסנן "הצג לפי משקיע" בטבלת ה-Positions.
- תשואה משוקללת זמן (TWR) לכל משקיע.
- מזומן ושווי במטבע שאינו USD (החלטה 11), כמה חשבונות IB, מחיקת משקיע.
