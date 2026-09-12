// Web Worker implementing the androidx.sqlite:sqlite-web
// (WebWorkerSQLiteDriver) message protocol over @sqlite.org/sqlite-wasm with
// an OPFS-backed database handle, giving Room 3 on wasmJs an origin-scoped
// persistent SQLite database.
//
// Vendored (2026-09-12,) from the official Room web demo
// https://github.com/danysantiago/room-web-demo (sqliteWasmWorker/worker.js,
// Apache-2.0) — that demo runs this file against the same Kotlin 2.3.21 +
// Room 3 toolchain this repo uses. Local deviations from the upstream file:
//   * VFS SELECTION — opfs-sahpool instead of the demo's proxy-VFS
//     `sqlite3.oo1.OpfsDb`. The proxy VFS requires SharedArrayBuffer, i.e. a
//     cross-origin-isolated context (COOP/COEP response headers); static
//     hosts this app ships on do not (must not) emit those — COEP
//     require-corp would break loading remote Jellyfin artwork URLs.
//     opfs-sahpool needs no special headers (plain SyncAccessHandles) and its
//     single-pool model matches the one-connection arrangement Room 3's
//     WebWorkerSQLiteDriver uses anyway (hasConnectionPool == false).
//     Inherited sahpool constraint: the pool is exclusive per browser
//     profile+directory, so a second app tab cannot open it concurrently —
//     web v1 ships single-tab. Verified end-to-end by
//     WebDatabaseRoundtripTest on wasmJsBrowserTest (ChromeHeadless, OPFS
//     live over localhost secure context).
//   * init/VFS failure answers queued protocol requests with an error
//     instead of leaving the Kotlin side awaiting forever.
//   * the per-message `console.log` of every protocol frame is removed (it
//     would spam the production console for every DAO call).
//   * nothing else — the protocol handlers (open/prepare/step/close) are
//     byte-equivalent to upstream.
// The protocol itself (request `{id, data:{cmd,...}}` / response
// `{id, data|error}`) is the stable contract documented on
// androidx.sqlite.driver.web.WebWorkerSQLiteDriver; this file is the only
// worker-side implementation shipped by this repo.
//
// Bundling: this file is consumed as a LOCAL npm package (npm(
// "jellyplay-sqlite-wasm-worker", <this dir>) on the database module's
// wasmJsMain) so webpack 5 assembles the whole chain — worker.js +
// sqlite3-bundler-friendly.mjs + the sqlite3.wasm binary — into
// self-contained sibling assets at build time. No runtime CDN fetches: every
// byte ships from this origin.
//
// DEV NOTE: Kotlin's yarn integration installs this directory as a COPY;
// after editing worker.js, refresh the install before a test run:
//   rm -rf build/wasm/node_modules && ./gradlew kotlinWasmNpmInstall

import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;
// The OPFS database class, set once the opfs-sahpool VFS is installed below.
let OpfsDbClass = null;

// Maps to track of active database connections and prepared statements by their unique IDs.
const databases = new Map(); // stores databaseId -> SQLiteDbObject
const statements = new Map(); // stores statementId -> SQLiteStatementObject

// Counters to generate unique IDs for new database connections and statements.
let nextDatabaseId = 0;
let nextStatementId = 0;

function openRequest(id, requestData) {
    try {
        const newDatabaseId = nextDatabaseId++;
        const newDatabase = new OpfsDbClass(requestData.fileName);
        databases.set(newDatabaseId, newDatabase);
        postMessage({'id': id, data: {'databaseId': newDatabaseId}});
    } catch (error) {
        postMessage({'id': id, error: error.message});
    }
}

function prepareRequest(id, requestData) {
    try {
        const newStatementId = nextStatementId++;
        const resultData = {
            'statementId': newStatementId,
            'parameterCount': 0,
            'columnNames': []
        };
        const database = databases.get(requestData.databaseId);
        if (!database) {
            postMessage({'id': id, error: "Invalid database ID: " + requestData.databaseId});
            return;
        }
        const statement = database.prepare(requestData.sql);
        statements.set(newStatementId, statement);
        resultData.parameterCount = sqlite3.capi.sqlite3_bind_parameter_count(statement);
        for (let i = 0; i < statement.columnCount; i++) {
            resultData.columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
        }
        postMessage({'id': id, data: resultData});
    } catch (error) {
        postMessage({'id': id, error: error.message});
    }
}

function stepRequest(id, requestData) {
    const statement = statements.get(requestData.statementId);
    if (!statement) {
        postMessage({'id': id, error: "Invalid statement ID: " + requestData.statementId});
        return;
    }
    try {
        const resultData = {
            'rows': [],
            'columnTypes': []
        };
        statement.reset()
        statement.clearBindings()
        for (let i = 0; i < requestData.bindings.length; i++) {
            statement.bind(i + 1, requestData.bindings[i]);
        }
        while (statement.step()) {
            if (!resultData.columnTypes.length) {
                for (let i = 0; i < statement.columnCount; i++) {
                    resultData.columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
                }
            }
            resultData.rows.push(statement.get([]));
        }
        postMessage({'id': id, data: resultData});
    } catch (error) {
        postMessage({'id': id, error: error.message});
    }
}

function closeRequest(id, requestData) {
    if (requestData.statementId != null) {
        const statement = statements.get(requestData.statementId);
        if (!statement) {
            postMessage({'id': id, error: "Invalid statement ID: " + requestData.statementId});
            return;
        }
        try {
            statement.finalize();
            statements.delete(requestData.statementId);
        } catch (error) {
            postMessage({'id': id, error: error.message});
        }
    }

    if (requestData.databaseId != null) {
        const database = databases.get(requestData.databaseId);
        if (!database) {
            postMessage({'id': id, error: "Invalid database ID: " + requestData.databaseId});
            return;
        }
        try {
            database.close();
            databases.delete(requestData.databaseId);
        } catch (error) {
            postMessage({'id': id, error: error.message});
        }
    }
}

// A map that links command names (strings) to their respective handler functions.
const commandMap = {
    'open': openRequest,
    'prepare': prepareRequest,
    'step': stepRequest,
    'close': closeRequest,
};

function handleMessage(e) {
    const requestMsg = e.data;
    if (!Object.hasOwn(requestMsg, 'data') || requestMsg.data == null) {
        postMessage(
            {'id': requestMsg.id, 'error': "Invalid request, missing 'data'."}
        );
        return;
    }
    if (!Object.hasOwn(requestMsg.data, 'cmd') || requestMsg.data.cmd == null) {
        postMessage(
            {'id': requestMsg.id, 'error': "Invalid request, missing 'cmd'."}
        );
        return;
    }
    const command = requestMsg.data.cmd;
    const requestHandler = commandMap[command];
    if (requestHandler) {
        requestHandler(requestMsg.id, requestMsg.data);
    } else {
        postMessage(
            {'id': requestMsg.id, 'error': "Invalid request, unknown command: '" + command + "'."}
        );
    }
}

// The sqlite3 module initializes asynchronously; requests arriving before it
// is ready are queued and replayed in order once initialization resolves.
const messageQueue = [];
// Set when sqlite3InitModule rejects; every request arriving afterwards is
// answered immediately with the recorded failure instead of queueing forever.
let initFailure = null;
onmessage = (e) => {
    if (initFailure) {
        postMessage({'id': e.data && e.data.id, error: initFailure});
    } else if (!sqlite3) {
        messageQueue.push(e);
    } else {
        handleMessage(e);
    }
};

// See the VFS SELECTION note in the header: opfs-sahpool (no COOP/COEP
// requirement) rather than the upstream demo's proxy-VFS OpfsDb.
sqlite3InitModule().then(async instance => {
    sqlite3 = instance;
    const poolUtil = await sqlite3.installOpfsSAHPoolVfs();
    OpfsDbClass = poolUtil.OpfsSAHPoolDb;
    while (messageQueue.length > 0) {
        handleMessage(messageQueue.shift());
    }
}).catch(err => {
    // Initialization/VFS failure must not leave requests awaiting a response
    // that will never come — answer everything queued so far, and every
    // late arrival (see the initFailure branch of onmessage), with the error.
    initFailure = 'sqlite-wasm init failed: ' + String((err && err.message) || err);
    while (messageQueue.length > 0) {
        const requestMsg = messageQueue.shift().data;
        if (requestMsg && requestMsg.id !== undefined) {
            postMessage({'id': requestMsg.id, error: initFailure});
        }
    }
    throw err;
});
