# VietSpecialty — Live Functional QA Execution Report (Milestones M2 & M4)

- **M2 Baseline Execution Date (UTC):** 2026-11-24
- **M4 Post-Fix Re-Verification Date (UTC):** 2026-11-24
- **Target Live Server:** `http://localhost:8080` (`d:\VietSpecialty\target\classes`, Spring Boot 3.4.2 / Java 21.0.8 / MySQL 8.0.43 / Flyway `V1..V3, V6..V8`)
- **Test Checklist Specification:** [`docs/FUNCTIONAL_TEST_CHECKLIST.md`](./FUNCTIONAL_TEST_CHECKLIST.md)
- **Execution Method:**
  - **Live HTTP/DB Sweep:** Deterministic Python `requests.Session` sweep across all 156 TCs (real CSRF extraction, multipart magic-byte uploads, local HMAC-SHA512 VNPay signing using `VIETSPECIALTYSANDBOXSECRETKEY2026`), paired with direct MySQL (`utf8mb4`) verification.
  - **Real Headless Browser DOM/JS Verification:** Node.js v24.18.0 native Chrome DevTools Protocol (CDP) driving `msedge.exe --headless=new` against `http://localhost:8080` (`DEF-14`, `DEF-19`, `DEF-20`, `DEF-21`, `DEF-23`, `DEF-11`).
  - **Automated Regression Suite:** Full `mvn test` in `d:\VietSpecialty`.
- **M2 Baseline Verdict (Pre-Fix):** **156 / 156** executed — **113 PASS** (72.4%), **36 FAIL** (23.1%), **7 BLOCKED/SKIPPED** (4.5%); **24 distinct defects** (`DEF-01`–`DEF-24`) logged as `OPEN`.
- **M4 Post-Fix Final Verdict:** **156 / 156** tracked — **155 PASS** (99.4%), **0 FAIL** (0.0%), **1 BLOCKED/SKIPPED** (0.6% — `TC-AUTH-10` interactive external Google OAuth2 login); **24 / 24 defects `FIXED (Verified Live)`**; **`mvn test`: `147 / 147` tests passing across `34` test classes (`0` failures, `0` errors, `0` skipped)**.

---

## 1. Execution Summary by Section (M2 Baseline vs. M4 Post-Fix)

| Section | Feature Area | Total | M2 Baseline (P / F / B) | M4 Post-Fix (P / F / B) | Defect IDs (`FIXED`) |
|---|---|---:|---|---|---|
| Section 1 | Guest & Public Storefront (`TC-GUEST-01`–`08`) | 8 | `6P / 2F / 0B` | **`8P / 0F / 0B`** | `DEF-23`, `DEF-24` |
| Section 2 | Authentication & Google OAuth2 (`TC-AUTH-01`–`16`) | 16 | `15P / 0F / 1B` | **`15P / 0F / 1B`** | — (`TC-AUTH-10` external OAuth2) |
| Section 3 | 4-Region & 34-Province Explorer (`TC-EXPLORE-01`–`06`) | 6 | `6P / 0F / 0B` | **`6P / 0F / 0B`** | — |
| Section 4 | Buyer Catalog, Reviews, Q&A, Wishlist, Cart & Checkout (`TC-BUY-01`–`31`) | 31 | `21P / 10F / 0B` | **`31P / 0F / 0B`** | `DEF-01`, `DEF-02`, `DEF-03`, `DEF-08`, `DEF-13`, `DEF-14`, `DEF-19`, `DEF-24` |
| Section 5 | Gift Wrapping & Greeting Card (`TC-GIFT-01`–`07`) | 7 | `2P / 4F / 1B` | **`7P / 0F / 0B`** | `DEF-01` |
| Section 6 | Payment Flows — COD & VNPay (`TC-PAY-01`–`09`) | 9 | `2P / 3F / 4B` | **`9P / 0F / 0B`** | `DEF-01`, `DEF-09`, `DEF-10` |
| Section 7 | Buyer Orders, Cancel, Reorder & Refund (`TC-ORD-01`–`11`) | 11 | `7P / 3F / 1B` | **`11P / 0F / 0B`** | `DEF-04`, `DEF-07`, `DEF-19` |
| Section 8 | Account Profile, Addresses, Password & Notifications (`TC-ACC-01`–`09`) | 9 | `7P / 2F / 0B` | **`9P / 0F / 0B`** | `DEF-11`, `DEF-15` |
| Section 9 | Shop Registration (`TC-SHOPREG-01`–`07`) | 7 | `6P / 1F / 0B` | **`7P / 0F / 0B`** | `DEF-05` |
| Section 10 | Seller Center (`TC-SEL-01`–`15`) | 15 | `13P / 2F / 0B` | **`15P / 0F / 0B`** | `DEF-19`, `DEF-20`, `DEF-21` |
| Section 11 | Admin Portal (`TC-ADM-01`–`13`) | 13 | `9P / 4F / 0B` | **`13P / 0F / 0B`** | `DEF-06`, `DEF-20`, `DEF-21`, `DEF-22` |
| Section 12 | Multi-turn RAG AI Chatbot (`TC-AI-01`–`11`) | 11 | `8P / 3F / 0B` | **`11P / 0F / 0B`** | `DEF-16`, `DEF-17`, `DEF-18` |
| Section 13 | RBAC & Cross-Tenant IDOR (`TC-RBAC-01`–`13`) | 13 | `11P / 2F / 0B` | **`13P / 0F / 0B`** | `DEF-11`, `DEF-12` |
| **Total** | **All 17 `PROJECT.md` Features** | **156** | **`113P / 36F / 7B`** | **`155P / 0F / 1B`** | **`DEF-01` .. `DEF-24` (24 / 24 FIXED)** |

---

## 2. External Integration Status (Local Runtime)

| Integration | Local `.env` / Runtime Config | Live Observed Behavior & Testing Boundaries |
|---|---|---|
| **Google OAuth2** | Real `GOOGLE_CLIENT_ID` & `GOOGLE_CLIENT_SECRET` configured (`isRealOAuthConfigured() == true`). | `GET /auth/google?mode=login&popup=true` returns `302` redirecting to `https://accounts.google.com/o/oauth2/v2/auth?...` with valid `client_id`, `redirect_uri=http://localhost:8080/auth/google/callback`, and `scope=openid email profile`. **Interactive Google account login (`TC-AUTH-10`) remains `BLOCKED/SKIPPED`** because unattended QA lacks interactive Google user credentials. Local chooser & popup `postMessage` endpoints (`/auth/google?local=true&popup=true`, `/auth/google/choose`, `POST /auth/google/confirm`, `GET/POST /auth/google/callback`) all executed live and passed (`TC-AUTH-11`–`16` `PASS`). |
| **VNPay Sandbox** | `.env` `VNPAY_TMN_CODE` & `VNPAY_HASH_SECRET` are empty; `AppProperties` falls back to `VIETSPEC` / `VIETSPECIALTYSANDBOXSECRETKEY2026` (`app.vnpay.process-on-return=true` in `dev`). | **M4 Live Update (`DEF-01`, `DEF-09`, `DEF-10` FIXED):** `POST /checkout/place-order` (`paymentMethod=VNPAY`) now generates and redirects (`302`) to `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...` (`TC-PAY-02` `PASS`). Local callbacks signed with HMAC-SHA512 (`VIETSPECIALTYSANDBOXSECRETKEY2026`) — `GET /payment/vnpay/return` (`00`, `24`, tampered hash) and `GET /api/payment/vnpay/ipn` (`00`, `02` replay, `97`, `01`, `04`, `24`, `405` on `POST`) — all executed live against `http://localhost:8080` and passed (`TC-PAY-03`–`09` `PASS`). **External Boundary:** completing payment inside the real hosted `sandbox.vnpayment.vn` UI remains out-of-scope locally because `VIETSPEC` is a placeholder merchant code. |
| **AI Chatbot / Gemini RAG** | `.env` `GEMINI_API_KEY` starts with `AQ.` (lacks `AIza` prefix), so `GeminiClient.isValidGoogleAiStudioKey()` returns `false`. | External Gemini LLM enhancement is **inactive** (`GeminiClient` returns `null`); all 11 chatbot test cases (`TC-AI-01`–`11`) executed 100% locally against `VietSpecialtyRagEngine` (`POST /api/chat`, `POST /api/chatbot`, `GET /api/chat/history`, `POST /api/chat/reset`) and passed in M4 after fixing `DEF-16`, `DEF-17`, and `DEF-18`. |
| **Image Storage** | `STORAGE_PROVIDER=local`, `CLOUDINARY_URL` empty. | Multipart uploads stored locally under `./uploads` and served via `/uploads/**`. |

---

## 3. Defect Log (`DEF-01` .. `DEF-24`)

### `DEF-01` — `POST /checkout/place-order` Crashes with HTTP 500 (`LazyInitializationException` on `Address.province`)
- **Severity:** `CRITICAL`
- **Related TC IDs:** `TC-BUY-26`, `TC-BUY-29`, `TC-BUY-30`, `TC-GIFT-01`, `TC-GIFT-02`, `TC-GIFT-03`, `TC-GIFT-07`, `TC-PAY-01`, `TC-PAY-02` (and previously blocked `TC-GIFT-06`, `TC-PAY-03`, `TC-PAY-04`, `TC-PAY-06`, `TC-PAY-07`, `TC-ORD-03`)
- **Reproduction Steps (M2):**
  1. Log in as `buyer@vietspecialty.vn` with an active default address (`addressId=1162`) and at least 1 item in cart.
  2. Fetch `GET /checkout` to obtain `_csrf` and `checkoutToken`.
  3. Submit `POST /checkout/place-order` (`COD`, `VNPAY`, or with `shopGifts[...]`).
- **Expected vs. Actual (M2):** Expected `302 /checkout/success?groupCode=...` (COD) or `302 https://sandbox.vnpayment.vn/...` (VNPAY); M2 returned HTTP `500` (`LazyInitializationException` on detached `Address.province` after `decrementStockAtomic` cleared the EntityManager).
- **Root Cause Reference:** `OrderServiceImpl.java`, `AddressRepository.java`, `CartItemRepository.java`.
- **Fix Summary & Modified Files:** Added `@EntityGraph(attributePaths = {"province"})` on `AddressRepository.findByIdAndUserId`; added `product.shop.owner` fetch graph on `CartItemRepository`; refactored `OrderServiceImpl.placeOrder` (`lines 207-259, 460-470`) to extract address/province scalars and use `userRepository.getReferenceById(...)` across `decrementStockAtomic`, and refactored `expireUnpaidOrderGroups` (`lines 851-939`) to iterate primitive group/order IDs across multi-shop orders.
- **Automated Regression Test(s):** `CheckoutVoucherShippingTest#testPlaceOrderNonTransactionalDetachmentAndCodPaymentGuard`, `VnpayAndScheduledJobsTest#testPaymentExpiryJobMultiShopAndMultiGroupNonTransactional`.
- **M4 Live Re-Verification Evidence:** All COD, multi-shop + `VIETSPECIALTY`, `checkoutToken` replay, gift wrapping (`STANDARD_BOX`, `BAMBOO_PREMIUM`, multi-shop selective, `isGift=false` ignore), and `VNPAY` redirect checkouts succeeded live: e.g., `TC-BUY-29` -> `302 /checkout/success?groupCode=VSG261124ODKKM6` (2 child orders `VS261124WM7CLV` `-40,385₫` & `VS261124ZATDFA` `-9,615₫`); `TC-PAY-02` -> `302 https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TmnCode=VIETSPEC&vnp_TxnRef=VSG2611248Y5AVR&vnp_Amount=8000000`; plus all 6 unblocked TCs (`TC-GIFT-06`, `TC-PAY-03,04,06,07`, `TC-ORD-03`) passed.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-02` — HTML Form Submission on `POST /products/{id}/comments` Returns HTTP 500
- **Severity:** `HIGH`
- **Related TC IDs:** `TC-BUY-14`, `TC-BUY-15`
- **Reproduction Steps (M2):** Submit standard HTML form `POST /products/59/comments` (without `Accept: application/json`) as Guest or `buyer@vietspecialty.vn`.
- **Expected vs. Actual (M2):** Expected `302 /login` (Guest) or `302 /products/{slug}#qa-section` (Buyer); M2 returned HTTP `500` because `ProductController.postProductComment` declared return type `Object` without `@ResponseBody`.
- **Fix Summary & Modified Files:** `ProductController.java:146-256` split into `postProductCommentJson` (`consumes = MediaType.APPLICATION_JSON_VALUE`, `@ResponseBody`) and `postProductCommentForm` returning `String` view/redirect names.
- **Automated Regression Test(s):** `ProductDetailWebTest#testPostProductCommentHtmlFormAndJson`.
- **M4 Live Re-Verification Evidence:** HTML form `POST /products/59/comments` as Buyer -> `302 http://localhost:8080/products/tra-dinh-non-tan-cuong-thuong-hang#qa-section` (`commentId=317`), reply `parentId=317` -> `302`; Guest HTML form -> `302 http://localhost:8080/login`; Guest JSON -> `401`; Buyer blank & `>1000`-char form -> `302` with flash error.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-03` — `GET /wishlist` Crashes with HTTP 500 When Non-Empty & Uses Volatile In-Memory Storage
- **Severity:** `HIGH`
- **Related TC IDs:** `TC-BUY-16`
- **Reproduction Steps (M2):** Toggle wishlist item `59` and request `GET /wishlist` or `GET /wishlist?ids=59,60`; inspect MySQL for wishlist persistence.
- **Expected vs. Actual (M2):** Expected `200 OK` rendering wishlisted products and DB persistence for logged-in buyers; M2 threw `LazyInitializationException` (`500`) and had 0 DB wishlist tables.
- **Fix Summary & Modified Files:** Added Flyway migration `src/main/resources/db/migration/V7__create_wishlists.sql`, `Wishlist.java`, `WishlistRepository.java`, `ProductRepository.findActiveWithDetailsByIds` (`JOIN FETCH` images/province/shop), and updated `WishlistController.java` with `@Transactional` DB persistence for authenticated users.
- **Automated Regression Test(s):** `OrderRefundAndWishlistRegressionTest#testWishlistDbPersistenceAndRendering`.
- **M4 Live Re-Verification Evidence:** Guest `POST /wishlist/toggle/59` -> `200`, `GET /wishlist` -> `200`. Buyer `POST /api/wishlist/toggle/59` -> `200`, MySQL `wishlists` persisted 1 row (`user_id=140, product_id=59`), survived fresh login (`GET /wishlist` -> `200` showing `Trà Đinh Nõn`), `GET /wishlist?ids=59,60` -> `200`, `POST /wishlist/clear` -> `302` (`0` DB rows).
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-04` — `POST /orders/{id}/reorder` Returns HTTP 500 When Past Order Contains a `HIDDEN` Product
- **Severity:** `HIGH`
- **Related TC IDs:** `TC-ORD-08`
- **Reproduction Steps (M2):** Seller toggles product `59` to `HIDDEN`; Buyer submits `POST /orders/309/reorder`.
- **Expected vs. Actual (M2):** Expected clean skip without `500`; M2 threw `UnexpectedRollbackException` (`500`) due to `@Transactional` on `OrderController.reorder`.
- **Fix Summary & Modified Files:** Removed `@Transactional` from `OrderController.java:79-123` and added `OrderRepository.findWithItemsAndProductsById` fetch graph so `cartService.addToCart` failures on `HIDDEN`/unavailable SKUs are skipped cleanly per item.
- **Automated Regression Test(s):** `OrderRefundAndWishlistRegressionTest#testReorderSkipsHiddenProductWithoutUnexpectedRollback`.
- **M4 Live Re-Verification Evidence:** With `pid=59` `HIDDEN`, `POST /orders/309/reorder` (`Accept: application/json`, single hidden item) returned `400` clean JSON (no `500`), and mixed-order reorder (`pid=59` + `pid=60`) returned `200` `{"success":true,"message":"Đã thêm 1 đặc sản vào giỏ hàng"}` skipping `pid=59`.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-05` — Shop Registration Returns HTTP 500 on Duplicate Name / Invalid Logo Magic Bytes & Accepts Invalid Hotline
- **Severity:** `HIGH`
- **Related TC IDs:** `TC-SHOPREG-03`
- **Reproduction Steps (M2):** Fresh `USER` submits `POST /account/shop-registration` with duplicate shop name, fake `.png` (`"not a png"`), or invalid `hotline="abc123"`.
- **Expected vs. Actual (M2):** Expected `200` form error for all invalid cases; M2 returned `500` on duplicate name / fake PNG (`@Transactional` rollback conflict) and accepted `hotline="abc123"`.
- **Fix Summary & Modified Files:** Removed `@Transactional` from `ShopRegistrationController.java` (delegating transactionally to `ShopServiceImpl`), and added `@Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$")` on `ShopRegisterRequest.java:20`.
- **Automated Regression Test(s):** `ShopRegistrationWebTest#testShopRegistrationNonTransactionalRollbackAndPhoneValidation`.
- **M4 Live Re-Verification Evidence:** Live `POST /account/shop-registration` with blank fields (`200`), duplicate name `"HTX Trà Tân Cương Thái Nguyên"` (`200`), `hotline="abc123"` (`200`), `hotline="0|12345678"` (`200`), and fake `.png` bytes `"not a png"` (`200`) all re-rendered the form with errors and created `0` shops in DB.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-06` — `GET /admin/moderation?tab=reviews` Aborts Mid-Stream with `LazyInitializationException` When Reviews Exist
- **Severity:** `HIGH`
- **Related TC IDs:** `TC-ADM-11`
- **Reproduction Steps (M2):** With >=1 review in `reviews`, request `GET /admin/moderation?tab=reviews` as `ADMIN`.
- **Expected vs. Actual (M2):** Expected `200 OK`; M2 aborted chunked response mid-stream (`LazyInitializationException` on `r.user` / `r.product`).
- **Fix Summary & Modified Files:** Added `@EntityGraph(attributePaths = {"user", "product"})` in `ReviewRepository.java:37-38` and `@Transactional(readOnly = true)` in `AdminModerationController.java:32-34`.
- **Automated Regression Test(s):** `AdminModerationRegressionTest#testAdminModerationReviewsAndCommentsWithoutLazyInitException`.
- **M4 Live Re-Verification Evidence:** With visible review `id=374` (`pid=59`), `GET /admin/moderation?tab=reviews` returned `200 OK` full HTML; toggling review `374` updated `products.id=59` `review_count` `1 -> 0 -> 1` cleanly.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-07` — `POST /orders/{id}/refund-request` Allows Refund Requests on `UNPAID` & Active `COD` Orders
- **Severity:** `HIGH`
- **Related TC IDs:** `TC-ORD-10`
- **Reproduction Steps (M2):** Buyer requests refund on `UNPAID` `COD` `PENDING_CONFIRM` order.
- **Expected vs. Actual (M2):** Expected `400 Bad Request`; M2 returned `200` and set `payment_status='REFUND_PENDING'`.
- **Fix Summary & Modified Files:** Added `paymentStatus == PAID && (status == DELIVERED || status == CANCELLED)` eligibility guard in `OrderController.java:125-168`.
- **Automated Regression Test(s):** `OrderRefundAndWishlistRegressionTest#testRefundRequestEligibilityGuards`.
- **M4 Live Re-Verification Evidence:** Buyer `POST /orders/308/refund-request` (`COD`, `PENDING_CONFIRM`, `UNPAID`) returned `400` (`Accept: application/json`) and `302 /orders/VS261118C4RT4Q` with flash error (HTML); DB `payment_status` remained `UNPAID`. Valid `PAID` + `DELIVERED` order (`VS2611240W0JAQ`) succeeded (`200`, `REFUND_PENDING`).
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-08` — Voucher `perUserLimit` Never Enforced or Recorded (`VoucherUsageRepository` Unused)
- **Severity:** `HIGH`
- **Related TC IDs:** `TC-BUY-26`
- **Reproduction Steps (M2):** Place multiple orders by the same buyer using `TANCUONG10` (`perUserLimit=1`).
- **Expected vs. Actual (M2):** Expected 2nd order rejected (`VOUCHER_USER_LIMIT_EXCEEDED`) and `voucher_usages` populated; M2 had `0` rows in `voucher_usages` and never checked `perUserLimit`.
- **Fix Summary & Modified Files:** Wired `VoucherUsageRepository` in `VoucherServiceImpl.java` (`perUserLimit` validation) and `OrderServiceImpl.java:286-345, 416-425, 699-733, 896-937` (persisting `APPLIED` on placeOrder and `RELEASED` on cancel/expiry).
- **Automated Regression Test(s):** `CheckoutVoucherShippingTest#testVoucherPerUserLimitMultiShopReleaseAndAvailabilityFilter`.
- **M4 Live Re-Verification Evidence:** 1st `COD` order with `TANCUONG10` succeeded (`302 /checkout/success?groupCode=VSG2611243YZQG2`, DB `voucher_usages` status=`APPLIED`); 2nd attempt with `TANCUONG10` was blocked (`302 -> /checkout` with error); cancelling 1st order transitioned `voucher_usages.status` to `RELEASED`.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-09` — `COD` Orders Hardcode `Payment.method = VNPAY` (Code & Schema)
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-PAY-01`
- **Reproduction Steps (M2):** Place `COD` order and inspect `OrderServiceImpl.java:448` / `payments` table.
- **Expected vs. Actual (M2):** Expected `COD` not to record `Payment.method = VNPAY`; M2 hardcoded `PaymentMethod.VNPAY`.
- **Fix Summary & Modified Files:** Guarded `Payment` entity creation in `OrderServiceImpl.java:473-483` with `if (paymentMethod == PaymentMethod.VNPAY)` so `COD` orders create no `VNPAY` payment row.
- **Automated Regression Test(s):** `CheckoutVoucherShippingTest#testPlaceOrderNonTransactionalDetachmentAndCodPaymentGuard`.
- **M4 Live Re-Verification Evidence:** Placed `COD` order `VSG261124A2CZXB` -> `302 /checkout/success`; DB `orders` `status=PENDING_CONFIRM`, `payment_status=UNPAID`, `payment_method=COD`, and `SELECT * FROM payments WHERE order_group_id=...` returned `0` rows.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-10` — VNPay IPN Error Responses Deviate from VNPay Specification (`99` instead of `97`/`01`, Missing `02`/`04`)
- **Severity:** `HIGH`
- **Related TC IDs:** `TC-PAY-08` (and `TC-PAY-07`)
- **Reproduction Steps (M2):** Call `GET /api/payment/vnpay/ipn` with invalid `vnp_SecureHash`, unknown `vnp_TxnRef`, mismatched `vnp_Amount`, or replay on `PAID` group.
- **Expected vs. Actual (M2):** Expected VNPay spec codes `97`, `01`, `04`, `02`; M2 returned `99` on `97`/`01` and omitted `04`/`02`.
- **Fix Summary & Modified Files:** Updated `VnpayServiceImpl.java:89-197` to return `"97"` (`Invalid Checksum`), `"01"` (`Order not found`), `"04"` (`Invalid amount`), `"02"` (`Order already confirmed`), and `"00"` (`Confirm Success`) including `vnp_ResponseCode=24` (`FAILED`).
- **Automated Regression Test(s):** `VnpayAndScheduledJobsTest#testVnpayIpnAndFailedReturn`.
- **M4 Live Re-Verification Evidence:** Live `GET /api/payment/vnpay/ipn`: tampered hash -> `{"RspCode":"97","Message":"Invalid Checksum"}`; `VSG_NOT_EXIST` -> `{"RspCode":"01","Message":"Order not found"}`; `vnp_Amount=100` -> `{"RspCode":"04","Message":"Invalid amount"}`; `vnp_ResponseCode=24` -> `{"RspCode":"00","Message":"Confirm Success"}` (`order_groups.payment_status='FAILED'`); replay on `PAID` (`TC-PAY-07`) -> `{"RspCode":"02","Message":"Order already confirmed"}`.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-11` — `ADMIN` Receives `403 Forbidden` on `/api/notifications/**` While Header Bell Is Rendered
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-ACC-08`, `TC-RBAC-06`
- **Reproduction Steps (M2):** Log in as `admin@vietspecialty.vn` and call `GET /api/notifications` & `POST /api/notifications/read-all`.
- **Expected vs. Actual (M2):** Expected `200 OK`; M2 returned `403 Forbidden` because `/api/notifications/**` required `ROLE_USER` (`ADMIN` does not imply `USER`).
- **Fix Summary & Modified Files:** Updated `SecurityConfig.java:58-62, 121-133` to allow `hasAnyRole("USER","SELLER","ADMIN")` on `/api/notifications/**` (keeping `RoleHierarchy` strictly `SELLER -> USER`) and added `data-notif-auth` in `fragments/header.html:5-38`.
- **Automated Regression Test(s):** `AuthAndSecurityWebTest#testAdminNotificationsAllowedWhileCartRemainsForbidden`.
- **M4 Live Re-Verification Evidence:** As `admin@vietspecialty.vn`, `GET /` renders `data-notif-auth="true"` & `data-auth="false"`; `GET /api/notifications` -> `200`, `POST /api/notifications/read-all` -> `200`, while `GET /cart` & `GET /api/cart/count` stay `403`. Verified in headless Edge DOM clicking notification bell (`200` network responses, zero `403`s).
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-12` — `SUSPENDED` Shop Owner Can Access `GET /seller/products` (`200 OK`)
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-RBAC-07`
- **Reproduction Steps (M2):** Log in as owner of a `SUSPENDED` shop and request `GET /seller/products`.
- **Expected vs. Actual (M2):** Expected `SHOP_NOT_APPROVED` block; M2 returned `200 OK`.
- **Fix Summary & Modified Files:** Enforced `requireApprovedShop(userId)` across all 7 endpoints in `SellerProductController.java:44-244`.
- **Automated Regression Test(s):** `SellerProductWebTest#testSuspendedAndPendingShopBlockedOnSellerProducts`.
- **M4 Live Re-Verification Evidence:** With test shop `id=2904` `SUSPENDED`, owner requests to `/seller/dashboard`, `/seller/products`, `/seller/products/create`, and `/seller/vouchers` all returned `400` (`SHOP_NOT_APPROVED`).
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-13` — Expired Active `PLATFORM` Vouchers Appear in Checkout Suggestions
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-BUY-27`
- **Reproduction Steps (M2):** Create an active `PLATFORM` voucher with `endAt` in 2025; load `GET /checkout` as `buyer`.
- **Expected vs. Actual (M2):** Expected expired voucher excluded; M2 rendered it in `systemVouchers`.
- **Fix Summary & Modified Files:** Updated `VoucherServiceImpl.java` (`getAvailableSystemVouchers()` & `getAvailableShopVouchers()`) to filter `isActive`, `startAt <= now <= endAt`, and `usedCount < usageLimit`.
- **Automated Regression Test(s):** `CheckoutVoucherShippingTest#testVoucherPerUserLimitMultiShopReleaseAndAvailabilityFilter`.
- **M4 Live Re-Verification Evidence:** Created active `PLATFORM` voucher `EXPIREDM4_5459` (`endAt=2025-12-31T23:59`, `is_active=1`); `GET /checkout` HTML did NOT contain `EXPIREDM4_5459`.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-14` — Checkout Reload on Shipping/Voucher Change Drops `addressId`, `paymentMethod`, and `note`
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-BUY-28`
- **Reproduction Steps (M2):** On `/checkout`, select non-default `addressId`, `paymentMethod`, and `note`, then trigger shipping or voucher reload.
- **Expected vs. Actual (M2):** Expected form selections retained across `GET /checkout?...` reloads and cleared on `/checkout/success`; M2 reset `addressId`, `paymentMethod`, and `note`.
- **Fix Summary & Modified Files:** Added `sessionStorage.vs_checkout_form_state` save/restore (`saveCheckoutFormState`, `restoreCheckoutFormState`) in `templates/web/checkout.html:503-584, 692-711` and cleared both `vs_checkout_gifts` and `vs_checkout_form_state` in `templates/web/order-success.html:84-89`.
- **Automated Regression Test(s):** `StaticPlaceholderResourcesTest#testCheckoutPersistenceAndLucideTemplatesInvariants`.
- **M4 Live Re-Verification Evidence:** Headless Edge CDP DOM test selected 2nd address (`1781`), `paymentMethod="COD"`, `note="Giao gio hanh chính DOM"`, and gift recipient `"Bac Hai DOM"`; after full reload via `shippingMethod=EXPRESS` AND second reload via `FREESHIP30`, all 4 fields restored accurately; after placing order (`/checkout/success?groupCode=VSG261124KD1X26`), both `vs_checkout_form_state` and `vs_checkout_gifts` became `null`.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-15` — Google OAuth2 Users Blocked from Updating Profile Without Phone or Setting Initial Password
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-ACC-02`
- **Reproduction Steps (M2):** Sign in as Google OAuth2 user (`phone=null`, random UUID `passwordHash`), submit `POST /account/profile` (`phone=""`) and `POST /account/password` (`oldPassword=""`).
- **Expected vs. Actual (M2):** Expected OAuth2 users can update name without phone and set initial password without knowing hidden random UUID; M2 blocked both.
- **Fix Summary & Modified Files:** Added Flyway `V8__add_password_set_to_users.sql`, `User.passwordSet`, updated `AuthServiceImpl.java:51,117`, `ProfileUpdateRequest.java` (optional blank phone), `ChangePasswordRequest.java`, `UserServiceImpl.java:41-81`, `AccountController.java:114-156`, `account/profile.html`, and `account/password.html`.
- **Automated Regression Test(s):** `BuyerOrdersAndAccountWebTest#testGoogleUserProfileWithoutPhoneAndInitialPasswordSetup`.
- **M4 Live Re-Verification Evidence:** New Google user (`google_m4_def15_1795535459@gmail.com`) started with `password_set=0, phone=NULL`; `POST /account/profile` (`phone=""`) -> `302 /account/profile` success; `GET /account/password` hid `oldPassword`; `POST /account/password` (`newPassword=Password123@`) -> `302 /account/password`, DB `password_set=1`; subsequent `POST /login` succeeded.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-16` — `GET /api/chat/history?chatKey=...` Leaks Authenticated User Chat History to Guests & Other Users
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-AI-10`
- **Reproduction Steps (M2):** User A sends `POST /api/chat` with `chatKey`; Guest and User B call `GET /api/chat/history?chatKey=...`.
- **Expected vs. Actual (M2):** Expected `401` (Guest) and `403` (User B); M2 returned `200 OK` leaking User A's history.
- **Fix Summary & Modified Files:** Added `conv.getUser()` ownership enforcement in `ChatApiController.java:48-73` and `ChatServiceImpl.java:70-79` (`401` Guest, `403` another user, `200` anonymous or owner).
- **Automated Regression Test(s):** `GeminiChatbotRagTest#testChatHistoryWithSuggestedProductsAndResetAndWidgetUi`, `BuyerOrdersAndAccountWebTest#testWishlistNotificationIdorAndChat`.
- **M4 Live Re-Verification Evidence:** Buyer-owned `chatKey`: Buyer `GET` -> `200`, Guest `GET` -> `401`, User B `GET` -> `403` & `POST` -> `403`, anonymous `chatKey` -> `200`, `POST /api/chat/reset` -> `200`.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-17` — AI Chatbot Calculation Ignores Explicitly Requested Voucher Code (`VIETSPECIALTY`)
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-AI-05`
- **Reproduction Steps (M2):** Ask `"Tính tiền nếu mình lấy 2 hộp đó và áp mã VIETSPECIALTY"` after discussing Trà Đinh Nõn (`850,000₫`).
- **Expected vs. Actual (M2):** Expected `VIETSPECIALTY` (`-50,000₫` -> `1,650,000₫`) applied; M2 auto-selected `TANCUONG10` (`1,600,000₫`).
- **Fix Summary & Modified Files:** Added `detectRequestedVoucher` in `VietSpecialtyRagEngine.java:834-890` (`synthesizeQuantityAndShippingCalculation`).
- **Automated Regression Test(s):** `GeminiChatbotRagTest#testExplicitRequestedVoucherInCalculation`.
- **M4 Live Re-Verification Evidence:** Live Turn 2 `POST /api/chat` (`"Tính tiền nếu mình lấy 2 hộp đó và áp mã VIETSPECIALTY"`) applied `VIETSPECIALTY` and returned `1.650.000₫`; unknown code `KHONGTONTAI99` also handled cleanly (`200`).
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-18` — AI Chatbot Fails `"trà sữa"` Unavailable Detection and Ignores `< 200k` Budget on `"rẻ hơn"`
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-AI-06`
- **Reproduction Steps (M2):** Send `"Có bán trà sữa không?"` and `"Có món nào rẻ hơn để làm quà dưới 200k không?"`.
- **Expected vs. Actual (M2):** Expected honest refusal for trà sữa and strictly `< 200,000₫` alternatives; M2 recommended tea/cakes without refusal and returned a `350,000₫` SKU.
- **Fix Summary & Modified Files:** Updated `VietSpecialtyRagEngine.java:437, 971-1003` with multi-word unavailable-dish matching (`"tra sua"`) and `BudgetFilter` enforcement in `synthesizeCheaperAlternatives`.
- **Automated Regression Test(s):** `GeminiChatbotRagTest#testUnavailableTraSuaAndCheaperWithinBudget`.
- **M4 Live Re-Verification Evidence:** Live `POST /api/chat`: `"Có bán trà sữa không?"` explicitly refused (`"chưa mở bán"`) + pivoted; `"Có món nào rẻ hơn để làm quà dưới 200k không?"` returned `[175000.0, 110000.0, 195000.0]` (all `<= 200,000₫`).
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-19` — Lucide `<i data-lucide="star">` Icons Inside Alpine `<template x-for>` Never Render & Seller Reviews Hardcodes 5 Stars
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-BUY-10`, `TC-ORD-11`, `TC-SEL-13`
- **Reproduction Steps (M2):** Inspect product detail reviews tab, order detail review modal, and `/seller/reviews` in headless Edge DOM.
- **Expected vs. Actual (M2):** Expected visible `<svg>` stars matching `rev.rating` and 5 clickable modal SVGs; M2 left unrendered `0x0` `<i>` elements and hardcoded 5 stars on `/seller/reviews`.
- **Fix Summary & Modified Files:** Replaced `<i data-lucide="star">` inside `<template x-for>` with inline `<svg>` in `web/product-detail.html`, `web/orders/detail.html`, and `#numbers.sequence(1, 5)` per `rev.rating` in `seller/reviews/index.html`; added `rAF`-debounced `[data-lucide]:not(svg)` `MutationObserver` across all 4 layouts (`layouts/base.html`, `layout/base.html`, `layouts/seller-base.html`, `layouts/admin-base.html`).
- **Automated Regression Test(s):** `StaticPlaceholderResourcesTest#testCheckoutPersistenceAndLucideTemplatesInvariants`, `SellerReviewWebAndApiTest#testSellerReviewsExactStarsAndSafeDataAttributes`.
- **M4 Live Re-Verification Evidence:** Headless Edge CDP verified: (a) `/products/tra-dinh-non-tan-cuong-thuong-hang` Reviews tab had `0` unrendered `[data-lucide]:not(svg)` and `15` visible `<svg>` stars (`width > 0`); (b) `/orders/VS2611240W0JAQ` modal rendered `5` clickable `32x32` `<svg>` stars toggling to `'Bình thường (3 sao)'` with 3 filled + 2 unfilled; (c) `/seller/reviews` `4/5` review rendered 5 `<svg>`s (`4` `fill-amber-400`, `1` `text-line`); (d) `MutationObserver` probe converted dynamic `<i data-lucide="check">` to `<svg>`.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-20` — Unsafe Thymeleaf Inline JS Attribute Interpolation Breaks Alpine `@click` on Quotes/Newlines
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-SEL-13`, `TC-ADM-08`
- **Reproduction Steps (M2):** Render a review with quotes/newline on `/seller/reviews` and a category named `Quà Tết "Đặc Biệt"` on `/admin/categories`.
- **Expected vs. Actual (M2):** Expected safe `data-*` attribute passing without JS syntax error; M2 broke `@click="openReply([[...]])"` / `openEdit([[...]])`.
- **Fix Summary & Modified Files:** Migrated `templates/seller/reviews/index.html:17-23,100-108` and `templates/admin/categories/index.html:17-23,96-101` to `th:attr="data-*=..."` + `$el.dataset.*`.
- **Automated Regression Test(s):** `SellerReviewWebAndApiTest#testSellerReviewsExactStarsAndSafeDataAttributes`, `AdminPortalWebTest#testAdminCategoryPageEscapesQuotesAndNewlinesViaDataAttributes`.
- **M4 Live Re-Verification Evidence:** Headless Edge CDP verified `0` JS exceptions on both `/seller/reviews` (review `Trà "ngon" 'tuyệt'!\nHương cốm...`) and `/admin/categories` (category `Quà Tết "Đặc Biệt" M4 459`, description `Mô tả 'test'\nDòng 2`); clicking both modal triggers opened Alpine modals with exact uncorrupted text/values.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-21` — Seller & Admin Voucher Form Validation Errors Vanish Silently and Inputs Reset
- **Severity:** `MEDIUM`
- **Related TC IDs:** `TC-SEL-12`, `TC-ADM-10`
- **Reproduction Steps (M2):** Submit invalid `POST /seller/vouchers/create` and `POST /admin/vouchers/create` (`code="A"`, `discountValue=0`, `name="Test Silent"`).
- **Expected vs. Actual (M2):** Expected `200 OK` displaying Vietnamese field validation errors and preserving entered inputs; M2 returned blank forms with errors dropped.
- **Fix Summary & Modified Files:** Added `"voucherForm"` default model attribute in `SellerVoucherController.java:60-118` and `AdminVoucherController.java:48-100`, and wired `th:object="${voucherForm}"`, `th:field`, `th:classappend`, and `th:errors` in `seller/vouchers/form.html` and `admin/vouchers/form.html`.
- **Automated Regression Test(s):** `SellerVoucherAndAnalyticsWebTest#testCreateSellerVoucherValidationAndDuplicates` & `testGetSellerVoucherCreateAndNewDefaults`, `AdminPortalWebTest#testAdminVoucherCreateValidationDuplicateToggleAndRbac`.
- **M4 Live Re-Verification Evidence:** Both live HTTP POST and headless Edge CDP form submissions (`noValidate = true`) returned `200 OK` preserving `name="Seller Silent DOM"` / `"Admin Silent DOM"` & `code="A"`, applying `border-rose-500`, and rendering `>= 2` `p.text-rose-600` field error messages.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-22` — Session Invalidation (`SessionInvalidator`) Returns HTTP `200` Plain Text Instead of `302 /login`
- **Severity:** `LOW`
- **Related TC IDs:** `TC-ADM-04`
- **Reproduction Steps (M2):** Lock an active user session via `POST /admin/users/{id}/lock`, then hit `GET /account/profile` with the existing cookie.
- **Expected vs. Actual (M2):** Expected `302 /login?expired` (web) / `401` (API); M2 returned `200 OK` 108B plain text.
- **Fix Summary & Modified Files:** Added `.expiredSessionStrategy(...)` in `SecurityConfig.java:158-171` (`302 /login?expired` for web, `401` JSON for `/api/**`) and `expired` banner handling in `AuthController.java:26-49`.
- **Automated Regression Test(s):** `AuthAndSecurityWebTest#testExpiredSessionStrategyForWebAndApi`.
- **M4 Live Re-Verification Evidence:** After `POST /admin/users/{id}/lock`, active session `GET /account/profile` returned `302 http://localhost:8080/login?expired` and `GET /api/notifications` returned `401`.
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-23` — Five Template-Referenced Fallback Placeholder Images Return `404 Not Found`
- **Severity:** `LOW`
- **Related TC IDs:** `TC-GUEST-02`
- **Reproduction Steps (M2):** Request `/images/demo/placeholder.svg`, `/images/demo/shop-placeholder.svg`, `/images/placeholder.png`, `/images/default-product.png`, `/images/default-shop.png`.
- **Expected vs. Actual (M2):** Expected `200 OK` valid SVG/PNG assets; M2 returned `404 Not Found` for all 5.
- **Fix Summary & Modified Files:** Created valid `.svg` and genuine Java `ImageIO`-rendered `400x400` `.png` files under `src/main/resources/static/images/`.
- **Automated Regression Test(s):** `StaticPlaceholderResourcesTest#testAllPlaceholderImagesServeSuccessfullyWithValidFormats`.
- **M4 Live Re-Verification Evidence:** Live HTTP `GET` returned `200 OK` with valid headers and magic bytes (`image/svg+xml` 1,060B & 1,181B; `image/png` `\x89PNG` 11,962B, 11,858B, 11,654B), and all 5 + `logo.svg` decoded in headless Edge DOM (`img.complete && img.naturalWidth > 0`, widths `150` / `400` / `300`).
- **Resolution Status:** `FIXED (Verified Live)`

---

### `DEF-24` — Non-Approved Shops & Hidden/Missing Products Return HTTP `400` (`error/500.html`) Instead of `404`
- **Severity:** `LOW`
- **Related TC IDs:** `TC-GUEST-04`, `TC-BUY-09`
- **Reproduction Steps (M2):** Request `GET /shops/{suspendedOrMissing}` and `GET /products/{hiddenOrMissing}`.
- **Expected vs. Actual (M2):** Expected HTTP `404` rendering `error/404.html`; M2 returned `400` rendering `error/500.html`.
- **Fix Summary & Modified Files:** Added local `@ExceptionHandler(BusinessException.class)` handlers in `ProductController.java:118-124` and `ShopController.java:28-55` returning `404` (`error/404`) for `PRODUCT_NOT_FOUND`, `SHOP_NOT_FOUND`, and `SHOP_NOT_APPROVED`.
- **Automated Regression Test(s):** `ProductDetailWebTest#testProductDetailNotFound`, `PublicCatalogAndCartApiTest#testShopDetailAndStatusHandling`, `AuthAndSecurityWebTest#testGlobalWebExceptionHandler`.
- **M4 Live Re-Verification Evidence:** Live `GET /shops/htx-test-qa-1795514630` (`SUSPENDED`) -> `404`, `GET /shops/khong-ton-tai` -> `404`, `GET /products/tra-shan-tuyet-co-thu-ta-xua-test` (`HIDDEN`) -> `404`, `GET /products/khong-ton-tai-slug` -> `404`.
- **Resolution Status:** `FIXED (Verified Live)`

---

## 4. Blocked / Skipped Test Cases Summary (M2 -> M4 Transition)

### 4.1 Unblocked & Verified PASS in M4 (6 TCs)

| TC ID | Prior M2 Blocker | M4 Live Verification Evidence | M4 Status |
|---|---|---|---|
| `TC-GIFT-06` | `DEF-01` | `GET /checkout/success?groupCode=VSG261124TDLHPW` -> `200` (clears `vs_checkout_gifts`), Buyer `GET /orders/VS261124N5B9L2` -> `200` (`Đối tác ABC`, `+25.000₫`), Seller `GET /seller/orders/VS261124N5B9L2` -> `200` (`Yêu cầu đóng gói quà`, `25.000₫`). | **PASS** |
| `TC-PAY-03` | `DEF-01` | Signed `GET /payment/vnpay/return` (`vnp_ResponseCode=00`, `VSG2611248Y5AVR`) -> `302 /checkout/success?groupCode=VSG2611248Y5AVR`; DB `order_groups=PAID`, child order `VS261124WLPH8Q` `PENDING_CONFIRM`/`PAID`, `payments.status='SUCCESS'`. | **PASS** |
| `TC-PAY-04` | `DEF-01` | Signed `GET /payment/vnpay/return` (`vnp_ResponseCode=24`) -> `200 payment-failed.html`; DB `order_groups=FAILED`, child order `PENDING_PAYMENT`/`UNPAID`. | **PASS** |
| `TC-PAY-06` | `DEF-01` | Unauthenticated `GET /api/payment/vnpay/ipn` (`vnp_ResponseCode=00`, valid HMAC, no CSRF) -> `200 {"RspCode":"00","Message":"Confirm Success"}`; DB `PAID` + `PENDING_CONFIRM`. | **PASS** |
| `TC-PAY-07` | `DEF-01` | Replay of `TC-PAY-06` signed IPN URL -> `200 {"RspCode":"02","Message":"Order already confirmed"}`; `order_status_history` count unchanged (`2 -> 2`). | **PASS** |
| `TC-ORD-03` | `DEF-01` | `VNPAY` `PENDING_PAYMENT` cancel -> `200` (`CANCELLED`, `UNPAID`); `PAID` `PENDING_CONFIRM` (`VS261124WLPH8Q`) cancel -> `200` (`CANCELLED`, `REFUND_PENDING`) and listed in `GET /admin/refunds`. | **PASS** |

### 4.2 Remaining External Integration Boundary (1 TC)

| TC ID | Blocker | Concrete Reason | M4 Status |
|---|---|---|---|
| `TC-AUTH-10` | External Google OAuth2 | `GET /auth/google?mode=login&popup=true` returned valid `302` to `https://accounts.google.com/o/oauth2/v2/auth?...` (`client_id`, `redirect_uri`, `scope=openid email profile`), while unattended QA cannot execute interactive Google account authentication without external user credentials. | **BLOCKED/SKIPPED** |

---

## 5. Automated Test Suite Verification (`mvn test`)

- **Command Executed:** `Set-Location d:\VietSpecialty; mvn test` (after restoring clean demo DB state)
- **Result:** **`BUILD SUCCESS`** (`Total time: 01:39 min`, finished `2026-11-24T22:56:51+07:00`)
- **Surefire Summary:** **`Tests run: 147, Failures: 0, Errors: 0, Skipped: 0`** across **`34`** test classes (`127` original suite tests + `20` M3 regression tests).

---

## 6. Non-Blocking Residual Observations (Post-M4 Hardening Notes)

1. **Sibling Seller Controllers (`DEF-12` scope note):** `SellerProductController`, `SellerDashboardController`, and `SellerVoucherController` enforce `ShopStatus.APPROVED` (passing all `TC-RBAC-07` checks), whereas 5 sibling controllers (`SellerOrderController`, `SellerReviewController`, `SellerQuestionController`, `SellerSettingController`, `SellerAnalyticsApiController`) still omit explicit `ShopStatus.APPROVED` guards.
2. **Pre-existing Phone Regex / Validation on Other DTOs:** `ShopRegisterRequest` and `ProfileUpdateRequest` use `^(0|\+84)[35789][0-9]{8}$`; `AddressRequest.java:14` and `RegisterRequest.java:19` still retain `[3|5|7|8|9]`, and `ShopUpdateRequest.java:17-18` uses `@NotBlank` only (0 of 156 TCs impacted).
3. **Low-Severity `VietSpecialtyRagEngine` & Template Pre-Existing Notes:** `ChatServiceImpl` checks attribute deep-dive before unavailable-dish detection on phrases like `"Trà sữa còn hàng không?"`, and `detectRequestedVoucher` requires an explicit code token (`VIETSPECIALTY`, etc.). In `orders/detail.html:323`, a static `<i data-lucide="rotate-Ccw">` uses PascalCase `Ccw` (unrelated to `DEF-19`).
4. **Pre-existing Dev Fixture Note (`products.id=351`):** `products.id=351` (`Trà Shan Tuyết Test 2`, `ACTIVE`, pre-dating M2) causes `TC-EXPLORE-01` / `/api/explore/regions` to report `31` active products (30 seeded + 1 dev SKU), and its local upload `/uploads/products/b282c00b-f706-47a4-9e53-b2a6a2e4efd3.jpg` is absent from `./uploads` (detected via headless Edge `DEF-23` DOM scan; all 6 static placeholder/logo assets return `200 OK` and decode cleanly).

---

## 7. Final Clean Demo DB & Live Server State Verification (Post-`mvn test`)

Verified after completing M4 live tests, restoring clean seeded DB state, and running full `mvn test`:
- **Live Server (`http://localhost:8080`):** Running cleanly in background (`PID 26748`, `GET / -> 200 OK`), logs outside repo.
- **Flyway Schema History:** `V1`..`V8` (`V1..V3, V6..V8` migrations + baseline `V4, V5`) all `success = 1`.
- **Demo Accounts (`ACTIVE`, `password_set=1`, plus OAuth demo `1693` `password_set=0`):**
  - `1`: `admin@vietspecialty.vn` (`ADMIN`, `ACTIVE`)
  - `140`: `buyer@vietspecialty.vn` (`USER`, `ACTIVE`)
  - `141`: `seller_bac@vietspecialty.vn` (`SELLER`, `ACTIVE`)
  - `142`: `seller_trung@vietspecialty.vn` (`SELLER`, `ACTIVE`)
  - `143`: `seller_nam@vietspecialty.vn` (`SELLER`, `ACTIVE`)
  - `1693`: `nguyenvanan.google@gmail.com` (`USER`, `ACTIVE`, `password_set=0`)
- **Demo Shops (`APPROVED`, original names):** `77` (`HTX Trà Tân Cương Thái Nguyên`), `78` (`Đặc Sản Cố Đô Thiên Hương Huế`), `79` (`Đặc Sản Miền Tây Út Mười`).
- **Master Data & Seeded Vouchers:** `8 / 8` categories `is_active=1`, `34` provinces, `3` seeded vouchers active (`VIETSPECIALTY`, `FREESHIP30`, `TANCUONG10`).
- **Buyer Clean State:** `buyer@vietspecialty.vn` (`140`) `cart_items = 0`, `wishlists = 0`, `addresses = 1` (`id=1162`, `Địa chỉ A`, `is_default=1`); `0` temporary M2/M4 QA users, shops, products, orders, categories, or provinces remain.
