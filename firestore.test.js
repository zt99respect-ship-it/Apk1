const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_1";
const BOB_UID = "bob_user_2";
const ADMIN_UID = "admin_rt_1";
const ADMIN_EMAIL = "mfr7h.carectar@gmail.com";
const FAKE_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, nowDate) {
  return {
    userId: uid,
    email: "alice@example.com",
    username: "ali_tr",
    passwordHash: FAKE_HASH,
    passwordSalt: "salt_12345678",
    secretCode: "TR-8942-ABCD",
    role: "user",
    blocked: false,
    appliedPatchVersion: 1,
    lastSeenAt: nowDate,
    createdAt: nowDate,
    updatedAt: nowDate,
  };
}

function validHotfixPayload(nowDate, version = 2) {
  return {
    patchVersion: version,
    patchNotes: "تحديث ساخن لتحسين الترجمة وتصفية الضوضاء",
    mandatoryHotfix: false,
    glossaryAdditions: { gg: "لعب رائع", clutch: "لقطة حاسمة" },
    noiseFilterAdditions: ["eh wallah", "ya shabab"],
    latestAppVersionName: "1.0.0",
    apkDownloadUrl: "https://github.com/owner/repo/releases/latest",
    controllerApkUrl: "https://github.com/owner/repo/releases/latest",
    updatedBy: "RT",
    createdAt: nowDate,
    updatedAt: nowDate,
  };
}

function validAppControlPayload(nowDate) {
  return {
    maintenanceEnabled: false,
    maintenanceMessage: "",
    allowAudioLive: true,
    allowScreenOcr: true,
    allowOverlayBubble: true,
    forceSkipArabic: true,
    forcedSpeechEngine: "AUTO",
    forcedSourceLanguage: "auto",
    remoteAudioChunkSeconds: 4,
    remoteScreenScanIntervalMs: 850,
    showGlobalBanner: false,
    globalBannerTitle: "",
    globalBannerMessage: "",
    githubRepoSlug: "owner/InstantTranslator",
    otaFeedUrl: "",
    updatedBy: "RT",
    createdAt: nowDate,
    updatedAt: nowDate,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("hotfix_profiles").doc("live").get());
  await assertFails(unauthDb.collection("app_control").doc("global").get());
  await assertFails(unauthDb.collection("remote_notifications").where("audience", "==", "all").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, now)));
});

test("User registration succeeds with valid hashed payload and rejects privilege escalation or cross-user access", async () => {
  const now = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();

  // Valid create
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, now)));

  // Reject role escalation on create
  const evilUser = { ...validUserPayload(BOB_UID, now), role: "admin" };
  await assertFails(bobDb.collection("users").doc(BOB_UID).set(evilUser));

  // Alice can update her own username/appliedPatchVersion, but cannot change secretCode or blocked
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "ali_pro",
      appliedPatchVersion: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      secretCode: "TR-0000-HACK",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot read Alice's profile or list users
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Email spoofing with unverified admin email is rejected", async () => {
  const now = new Date(Date.now() - 2000);
  const spoofDb = testEnv.authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  await assertFails(spoofDb.collection("hotfix_profiles").doc("live").set(validHotfixPayload(now)));
  await assertFails(spoofDb.collection("app_control").doc("global").set(validAppControlPayload(now)));
});

test("Verified Admin can publish hotfixes, update global app_control, manage users, and push targeted & global notifications", async () => {
  const now = new Date(Date.now() - 2000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, now)));

  // Admin publishes hotfix profile and global app_control
  await assertSucceeds(adminDb.collection("hotfix_profiles").doc("live").set(validHotfixPayload(now, 2)));
  await assertSucceeds(adminDb.collection("app_control").doc("global").set(validAppControlPayload(now)));

  // Authenticated user can read hotfix_profiles/live & app_control/global via get, but cannot write or list
  await assertSucceeds(aliceDb.collection("hotfix_profiles").doc("live").get());
  await assertSucceeds(aliceDb.collection("app_control").doc("global").get());
  await assertFails(aliceDb.collection("hotfix_profiles").get());
  await assertFails(aliceDb.collection("hotfix_profiles").doc("live").update({ patchVersion: 99, updatedAt: now }));

  // Admin lists users & blocks Alice
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      blocked: true,
      blockedReason: "تم الإيقاف مؤقتاً من لوحة التحكم",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin pushes broadcast notification; Alice queries with audience=='all' filter (unfiltered list fails)
  await assertSucceeds(
    adminDb.collection("remote_notifications").doc("notif_1").set({
      audience: "all",
      title: "إشعار هام",
      body: "يرجى تطبيق التحديث الساخن",
      category: "update",
      updateAlert: true,
      senderLabel: "RT",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("remote_notifications").where("audience", "==", "all").get());
  await assertFails(aliceDb.collection("remote_notifications").get());

  // Admin pushes direct inbox message to Alice; Alice reads & marks read, Bob cannot read
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).collection("inbox").doc("msg_1").set({
      recipientUid: ALICE_UID,
      title: "رسالة خاصة",
      body: "مرحباً بك",
      category: "direct",
      read: false,
      senderLabel: "RT",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("inbox").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inbox").doc("msg_1").update({
      read: true,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("inbox").get());
});
