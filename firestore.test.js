const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_alice";
const EDITOR_UID = "editor_bob";
const VIEWER_UID = "viewer_carol";
const OUTSIDER_UID = "outsider_eve";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

function validProjectPayload(ownerUid = OWNER_UID, inviteCode = "BLD26A") {
  const ts = pastDate(5000);
  return {
    ownerId: ownerUid,
    name: "Google Buildathon App",
    description: "Minimalist ADHD-friendly sprint workspace",
    sprintName: "Sprint 1 - Core MVP",
    sprintDeadlineMs: Date.now() + 7 * 86400000,
    githubOwner: "android",
    githubRepo: "compose-samples",
    inviteCode,
    webhookUrl: "",
    memberUids: [ownerUid],
    roles: { [ownerUid]: "OWNER" },
    memberNames: { [ownerUid]: "Alice" },
    milestoneEmails: ["team@example.com"],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validTaskPayload(projectId, creatorUid, memberUids) {
  const ts = pastDate(4000);
  return {
    projectId,
    creatorId: creatorUid,
    title: "Ship Firestore offline sync",
    description: "Enable persistent local cache for tasks",
    status: "TODO",
    priority: "HIGH",
    assigneeUid: creatorUid,
    assigneeName: "Alice",
    dueDateMs: Date.now() + 86400000,
    estimateMinutes: 25,
    milestone: true,
    focused: true,
    substeps: ["Configure cache", "Test airplane mode"],
    completedSubsteps: [],
    githubIssueNumber: 0,
    memberUids,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validFilePayload(projectId, creatorUid, memberUids) {
  const ts = pastDate(4000);
  return {
    projectId,
    creatorId: creatorUid,
    name: "Architecture.md",
    path: "docs/Architecture.md",
    fileType: "DOC",
    url: "https://github.com/android/compose-samples",
    notes: "Core MVVM + Firestore offline design doc",
    tags: ["architecture", "mvp"],
    memberUids,
    createdAt: ts,
    updatedAt: ts,
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

// --- 1. UNAUTHENTICATED DENIAL ---
test("Unauthenticated users are blocked from all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(OWNER_UID).get());
  await assertFails(unauth.collection("projects").get());
  await assertFails(unauth.collection("project_invites").doc("BLD26A").get());
  await assertFails(
    unauth.collection("projects").doc("proj_1").set(validProjectPayload())
  );
});

// --- 2. PRIVATE USER PROFILE & PII ISOLATION ---
test("User profile: owner can create/update/read; others denied; shadow field rejected", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const ts = pastDate(3000);

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      displayName: "Alice",
      email: "alice@example.com",
      focusMinutes: 25,
      reminderIntervalMinutes: 60,
      calmModeEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).update({
      focusMinutes: 15,
      calmModeEnabled: false,
      updatedAt: pastDate(500),
    })
  );

  // PII isolation: outsider cannot read or list users
  await assertFails(outsiderDb.collection("users").doc(OWNER_UID).get());
  await assertFails(outsiderDb.collection("users").get());

  // Shadow field attack rejected
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).update({
      isAdmin: true,
      updatedAt: pastDate(200),
    })
  );

  // Future timestamp tampering rejected
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).update({
      focusMinutes: 30,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

// --- 3. PROJECT CREATION, INVITE SELF-JOIN & ANTI-ESCALATION ---
test("Projects: owner creates, creates invite, teammate self-joins as EDITOR only; escalation fails", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const bobDb = testEnv.authenticatedContext(EDITOR_UID).firestore();
  const eveDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Owner creates project (no @DocumentId `id` field!)
  await assertSucceeds(
    ownerDb.collection("projects").doc("proj_1").set(validProjectPayload())
  );

  // Rejects redundant `id` field on create
  await assertFails(
    ownerDb.collection("projects").doc("proj_bad").set({
      ...validProjectPayload(),
      id: "proj_bad",
    })
  );

  // Owner creates invite code lookup
  await assertSucceeds(
    ownerDb.collection("project_invites").doc("BLD26A").set({
      projectId: "proj_1",
      ownerId: OWNER_UID,
      projectName: "Google Buildathon App",
      createdAt: pastDate(2000),
    })
  );

  // Invite collection cannot be listed/scraped
  await assertFails(bobDb.collection("project_invites").get());
  // Valid invite code can be fetched by exact key
  await assertSucceeds(bobDb.collection("project_invites").doc("BLD26A").get());

  // Privilege escalation attempt: Bob tries to self-join as ADMIN -> MUST FAIL
  await assertFails(
    bobDb.collection("projects").doc("proj_1").update({
      memberUids: [OWNER_UID, EDITOR_UID],
      [`roles.${EDITOR_UID}`]: "ADMIN",
      [`memberNames.${EDITOR_UID}`]: "Bob",
      updatedAt: pastDate(500),
    })
  );

  // Legitimate invite self-join as EDITOR -> SUCCEEDS
  await assertSucceeds(
    bobDb.collection("projects").doc("proj_1").update({
      memberUids: [OWNER_UID, EDITOR_UID],
      [`roles.${EDITOR_UID}`]: "EDITOR",
      [`memberNames.${EDITOR_UID}`]: "Bob",
      updatedAt: pastDate(500),
    })
  );

  // Filtered Kotlin query succeeds for members; unfiltered list fails
  await assertSucceeds(
    bobDb
      .collection("projects")
      .where("memberUids", "array-contains", EDITOR_UID)
      .get()
  );
  await assertFails(bobDb.collection("projects").get());

  // Outsider cannot read project
  await assertFails(eveDb.collection("projects").doc("proj_1").get());
});

// --- 4. RBAC ON TASKS & PROJECT FILES (OWNER / EDITOR / VIEWER) ---
test("RBAC enforcement: OWNER & EDITOR write tasks/files; VIEWER is strictly read-only", async () => {
  const ts = pastDate(10000);
  const allMembers = [OWNER_UID, EDITOR_UID, VIEWER_UID];

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("projects").doc("proj_rbac").set({
      ...validProjectPayload(OWNER_UID, "RBAC99"),
      memberUids: allMembers,
      roles: {
        [OWNER_UID]: "OWNER",
        [EDITOR_UID]: "EDITOR",
        [VIEWER_UID]: "VIEWER",
      },
      memberNames: {
        [OWNER_UID]: "Alice",
        [EDITOR_UID]: "Bob",
        [VIEWER_UID]: "Carol",
      },
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const editorDb = testEnv.authenticatedContext(EDITOR_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // OWNER creates a task & file
  await assertSucceeds(
    ownerDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("tasks")
      .doc("task_1")
      .set(validTaskPayload("proj_rbac", OWNER_UID, allMembers))
  );
  await assertSucceeds(
    ownerDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("files")
      .doc("file_1")
      .set(validFilePayload("proj_rbac", OWNER_UID, allMembers))
  );

  // EDITOR can create & update task status, but CANNOT alter memberUids or project settings
  await assertSucceeds(
    editorDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("tasks")
      .doc("task_1")
      .update({
        status: "DONE",
        completedSubsteps: ["Configure cache", "Test airplane mode"],
        updatedAt: pastDate(300),
      })
  );
  await assertFails(
    editorDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("tasks")
      .doc("task_1")
      .update({
        memberUids: [EDITOR_UID],
        updatedAt: pastDate(200),
      })
  );
  await assertFails(
    editorDb.collection("projects").doc("proj_rbac").update({
      name: "Hijacked Name",
      updatedAt: pastDate(200),
    })
  );

  // VIEWER can read tasks & files via Kotlin memberUids query, CANNOT create/update/delete
  await assertSucceeds(
    viewerDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("tasks")
      .where("memberUids", "array-contains", VIEWER_UID)
      .get()
  );
  await assertSucceeds(
    viewerDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("files")
      .where("memberUids", "array-contains", VIEWER_UID)
      .get()
  );
  await assertFails(
    viewerDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("tasks")
      .doc("task_viewer")
      .set(validTaskPayload("proj_rbac", VIEWER_UID, allMembers))
  );
  await assertFails(
    viewerDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("tasks")
      .doc("task_1")
      .update({ status: "TODO", updatedAt: pastDate(200) })
  );
  await assertFails(
    viewerDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("files")
      .doc("file_viewer")
      .set(validFilePayload("proj_rbac", VIEWER_UID, allMembers))
  );

  // Unfiltered list on tasks/files fails even for owner
  await assertFails(
    ownerDb.collection("projects").doc("proj_rbac").collection("tasks").get()
  );
  // Outsider blocked from everything
  await assertFails(
    outsiderDb
      .collection("projects")
      .doc("proj_rbac")
      .collection("tasks")
      .doc("task_1")
      .get()
  );
});
