-- BookEasy demo offers for the demo businesses (gym packages, free check-up camps, salon discounts, trial weeks, ...).
-- Load after the other demo files. Safe to re-run: it replaces the offers of demo businesses only. Fictional; never run
-- against production. Dates are relative to the day it is loaded.
SET NAMES utf8mb4;
START TRANSACTION;

CREATE TEMPORARY TABLE demo_promo_orgs AS
SELECT DISTINCT o.id, o.subcategory_id AS sub FROM organizations o
JOIN organization_owners oo ON oo.organization_id = o.id JOIN users u ON u.id = oo.user_id
WHERE u.email LIKE 'demo.%@example.com' AND o.verification_status = 'APPROVED';

DELETE FROM promotions WHERE organization_id IN (SELECT id FROM demo_promo_orgs);

-- (subcategory, variant, kind, percent, title, details, days valid). Each business gets the variant MOD(id, 3) of its
-- type plus, for every second business, the variant after it, so neighbours don't all show the same deal.
CREATE TEMPORARY TABLE demo_promo_templates (
    sub INT, variant INT, kind VARCHAR(10), pct INT NULL, title VARCHAR(100), details VARCHAR(300), days INT);
INSERT INTO demo_promo_templates VALUES
 (1, 0, 'FREE', NULL, 'Free health check-up camp this Sunday', 'BP, blood sugar, BMI and a doctor''s consultation – free, 8 am to 1 pm', 6),
 (1, 1, 'FREE', NULL, 'Free BP and sugar check for seniors', 'Every weekday morning for anyone over 60', 30),
 (1, 2, 'DISCOUNT', 20, '20% off master health check-up', 'Full body check-up package with reports in 24 hours', 25),
 (2, 0, 'FREE', NULL, 'Free dental check-up', 'Check-up and advice from our dentist, no charge', 20),
 (2, 1, 'DISCOUNT', 15, '15% off teeth cleaning', 'Scaling and polishing this month', 30),
 (2, 2, 'PACKAGE', NULL, 'Family dental plan: 4 check-ups for the price of 3', 'For up to four family members', 45),
 (3, 0, 'FREE', NULL, 'Free eye test with any frame', 'Computerised eye test, free when you buy a frame', 30),
 (3, 1, 'FREE', NULL, 'Free eye screening camp for children', 'Saturday 9 am to 12 pm', 8),
 (3, 2, 'DISCOUNT', 25, '25% off spectacle lenses', 'Anti-glare and blue-cut lenses', 20),
 (4, 0, 'DISCOUNT', 40, '40% off full body check-up', '72 tests including thyroid, lipid and sugar', 20),
 (4, 1, 'FREE', NULL, 'Free home sample collection', 'Within 10 km, for tests above ₹500', 45),
 (4, 2, 'PACKAGE', NULL, 'Couple health package at ₹2,499', 'Two full body check-ups', 30),
 (5, 0, 'DISCOUNT', 20, '20% off on weekdays', 'Haircut, colour and spa, Monday to Thursday', 30),
 (5, 1, 'PACKAGE', NULL, 'Haircut + beard + head massage at ₹399', 'Grooming combo', 20),
 (5, 2, 'FREE', NULL, 'Free hair wash with any haircut', 'This month', 30),
 (6, 0, 'PACKAGE', NULL, 'Bridal package with free trial makeup', 'Book the bridal package and get a trial session free', 60),
 (6, 1, 'DISCOUNT', 25, '25% off facials', 'All facials, weekdays', 20),
 (6, 2, 'FREE', NULL, 'Free threading with any facial', 'This month', 30),
 (7, 0, 'PACKAGE', NULL, 'Buy 2 massages, get 1 free', 'Valid for three months from purchase', 30),
 (7, 1, 'DISCOUNT', 30, '30% off couple spa on weekdays', 'Monday to Thursday', 20),
 (7, 2, 'FREE', NULL, 'Free foot reflexology with a full body massage', 'This month', 30),
 (8, 0, 'FREE', NULL, 'Free demo classes all week', 'Attend any batch free for a week before joining', 14),
 (8, 1, 'DISCOUNT', 10, '10% off early admission', 'For the next academic year, before the end of the month', 25),
 (8, 2, 'EVENT', NULL, 'Free career guidance seminar', 'Saturday 4 pm, for students of classes 10–12 and parents', 7),
 (9, 0, 'FREE', NULL, 'Free first lesson', 'Try a class before you join', 30),
 (9, 1, 'EVENT', NULL, 'Summer workshop: 10 days for ₹1,499', 'Beginners, ages 6 and up', 30),
 (9, 2, 'DISCOUNT', 15, '15% off for siblings', 'When two or more children join', 45),
 (10, 0, 'PACKAGE', NULL, 'Pay for 6 months, get 6 months free', '12 months of gym access for the price of 6 – limited period', 21),
 (10, 1, 'FREE', NULL, 'Free 7-day trial pass', 'Full gym access with a trainer walkthrough', 30),
 (10, 2, 'DISCOUNT', 30, '30% off annual membership', 'Includes a body composition analysis', 25),
 (11, 0, 'FREE', NULL, 'Free 3-day yoga trial', 'Any morning or evening batch', 30),
 (11, 1, 'DISCOUNT', 25, '25% off quarterly yoga plan', 'Hatha and power yoga batches', 30),
 (11, 2, 'EVENT', NULL, 'Free meditation session this Sunday', '7 am, open to everyone', 6),
 (13, 0, 'EVENT', NULL, 'Live music night this Friday', 'No entry fee – book a table', 6),
 (13, 1, 'PACKAGE', NULL, 'Family thali for 4 at ₹999', 'Weekend lunch', 30),
 (13, 2, 'FREE', NULL, 'Free dessert on birthdays', 'Show your ID at the table', 60),
 (14, 0, 'PACKAGE', NULL, 'Coffee + croissant at ₹199', 'Breakfast combo until 11 am', 30),
 (14, 1, 'FREE', NULL, 'Every 10th coffee free', 'Ask for a loyalty card', 60),
 (14, 2, 'EVENT', NULL, 'Open mic evening on Saturday', '6 pm, free entry', 7);

INSERT INTO promotions (organization_id, title, details, kind, percent_off, valid_from, valid_until, created_at)
SELECT d.id, t.title, t.details, t.kind, t.pct, UTC_DATE(), UTC_DATE() + INTERVAL t.days DAY, UTC_TIMESTAMP(6)
FROM demo_promo_orgs d JOIN demo_promo_templates t ON t.sub = d.sub AND t.variant = MOD(d.id, 3);

INSERT INTO promotions (organization_id, title, details, kind, percent_off, valid_from, valid_until, created_at)
SELECT d.id, t.title, t.details, t.kind, t.pct, UTC_DATE(), UTC_DATE() + INTERVAL t.days DAY, UTC_TIMESTAMP(6)
FROM demo_promo_orgs d JOIN demo_promo_templates t ON t.sub = d.sub AND t.variant = MOD(d.id + 1, 3)
WHERE MOD(d.id, 2) = 0;

DROP TEMPORARY TABLE demo_promo_templates;
DROP TEMPORARY TABLE demo_promo_orgs;
COMMIT;
