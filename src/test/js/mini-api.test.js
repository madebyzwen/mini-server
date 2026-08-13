"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const libraryPath = path.resolve(__dirname, "../../../www/_shared/mini-api.js");
const librarySource = fs.readFileSync(libraryPath, "utf8");
const tests = [];

function test(name, body) {
    tests.push({name, body});
}

function response(status, body, options) {
    const settings = options || {};
    return {
        status,
        text() {
            if (settings.textCalls) {
                settings.textCalls.count += 1;
            }
            if (settings.textError) {
                return Promise.reject(settings.textError);
            }
            return Promise.resolve(body === undefined ? "" : body);
        }
    };
}

function load(pathname, responder) {
    const requests = [];
    const window = {
        location: {pathname},
        fetch(url, options) {
            requests.push({url, options});
            return responder
                    ? responder(url, options, requests.length - 1)
                    : Promise.resolve(response(204));
        }
    };
    const context = vm.createContext({window});
    vm.runInContext(librarySource, context, {filename: libraryPath});
    return {
        MiniApi: window.MiniApi,
        Promise: vm.runInContext("Promise", context),
        requests,
        window
    };
}

async function rejects(operation, verify) {
    let failure;
    try {
        await operation;
    } catch (error) {
        failure = error;
    }
    assert.ok(failure, "Expected the operation to reject.");
    if (verify) {
        verify(failure);
    }
}

function assertJsonValue(actual, expected) {
    assert.equal(JSON.stringify(actual), JSON.stringify(expected));
}

test("exposes only the operation-first public API", () => {
    const {MiniApi} = load("/example/");
    assert.deepEqual(
            Object.keys(MiniApi).sort(),
            ["clear", "read", "readAll", "remove", "write"]);
    assert.equal(MiniApi.private, undefined);
    assert.equal(MiniApi.shared, undefined);
    assert.equal(MiniApi.readSection, undefined);
    assert.equal(MiniApi.removeSection, undefined);
});

test("does not fetch until a terminal scope is selected", () => {
    const loaded = load("/example/");
    loaded.MiniApi.read("settings");
    loaded.MiniApi.readAll();
    loaded.MiniApi.write({settings: true});
    loaded.MiniApi.remove("settings");
    loaded.MiniApi.clear();
    assert.equal(loaded.requests.length, 0);
});

test("terminal scopes always return native Promises", async () => {
    const loaded = load("/example/", () => Promise.resolve(response(200, "{}")));
    const privateResult = loaded.MiniApi.readAll().private();
    const sharedResult = loaded.MiniApi.readAll().shared();
    const invalidResult = loaded.MiniApi.read(null).shared();
    assert.ok(privateResult instanceof loaded.Promise);
    assert.ok(sharedResult instanceof loaded.Promise);
    assert.ok(invalidResult instanceof loaded.Promise);
    await rejects(invalidResult);
    await Promise.all([privateResult, sharedResult]);
});

test("derives the first site segment for root and nested application pages", async () => {
    for (const pathname of ["/example/", "/example/index.html", "/example/pages/settings.html"]) {
        const loaded = load(pathname, () => Promise.resolve(response(200, "{}")));
        await loaded.MiniApi.readAll().shared();
        assert.equal(loaded.requests[0].url, "/example/api/shared/readAll");
    }

    const second = load("/notes/page.html", () => Promise.resolve(response(200, "{}")));
    await second.MiniApi.readAll().private();
    assert.equal(second.requests[0].url, "/notes/api/private/readAll");
});

test("rejects unusable application locations without fetching", async () => {
    for (const pathname of ["/", "/_shared/mini-api.js", "/%/"]) {
        const loaded = load(pathname);
        await rejects(loaded.MiniApi.readAll().shared());
        assert.equal(loaded.requests.length, 0);
    }
});

test("read uses both scoped GET routes and resolves every native JSON value", async () => {
    const values = [
        {value: {theme: "dark"}},
        {value: ["A", "B"]},
        {value: "hello"},
        {value: 42},
        {value: true},
        {value: null}
    ];
    for (const item of values) {
        const loaded = load("/example/", () =>
            Promise.resolve(response(200, JSON.stringify(item.value))));
        const result = await loaded.MiniApi.read("settings").shared();
        assertJsonValue(result, item.value);
        assert.equal(loaded.requests[0].url, "/example/api/shared/read?section=settings");
        assert.equal(loaded.requests[0].options.method, "GET");
    }

    const privateRead = load("/example/", () =>
        Promise.resolve(response(200, "null")));
    assert.equal(await privateRead.MiniApi.read("settings").private(), null);
    assert.equal(
            privateRead.requests[0].url,
            "/example/api/private/read?section=settings");
});

test("read URL-encodes special and path-looking Section names", async () => {
    const section = "café / & = ? #";
    const loaded = load("/example/", () => Promise.resolve(response(200, "true")));
    await loaded.MiniApi.read(section).shared();
    assert.equal(
            loaded.requests[0].url,
            "/example/api/shared/read?section=" + encodeURIComponent(section));
    assert.equal(loaded.requests[0].url.split("?").length, 2);
});

test("URL encoding failures reject without fetching", async () => {
    const loaded = load("/example/");
    await rejects(loaded.MiniApi.read("unpaired\ud800").shared());
    assert.equal(loaded.requests.length, 0);
});

test("read and readAll reject missing or malformed successful JSON", async () => {
    for (const body of ["", "not json"] ) {
        const loaded = load("/example/", () => Promise.resolve(response(200, body)));
        await rejects(loaded.MiniApi.readAll().shared());
        await rejects(loaded.MiniApi.read("value").shared());
    }
});

test("readAll resolves an empty persistence object", async () => {
    const loaded = load("/example/", () => Promise.resolve(response(200, "{}")));
    const result = await loaded.MiniApi.readAll().private();
    assertJsonValue(result, {});
    assert.equal(loaded.requests[0].url, "/example/api/private/readAll");
    assert.equal(loaded.requests[0].options.method, "GET");
});

test("write serializes one or many Sections with the exact POST contract", async () => {
    const data = {settings: {theme: "dark"}, items: ["A", "B"], optional: null};
    const textCalls = {count: 0};
    const loaded = load("/example/", () =>
        Promise.resolve(response(204, "must not parse", {textCalls})));
    const result = await loaded.MiniApi.write(data).shared();
    assert.equal(result, undefined);
    assert.equal(loaded.requests[0].url, "/example/api/shared/write");
    assert.equal(loaded.requests[0].options.method, "POST");
    assert.equal(
            loaded.requests[0].options.headers["Content-Type"],
            "application/json; charset=utf-8");
    assert.deepEqual(JSON.parse(loaded.requests[0].options.body), data);
    assert.equal(textCalls.count, 0);

    const one = load("/example/", () => Promise.resolve(response(204)));
    await one.MiniApi.write({value: true}).private();
    assert.equal(one.requests[0].url, "/example/api/private/write");
});

test("write root validation rejects asynchronously without fetching", async () => {
    for (const value of [null, [], "text", 1, true, undefined, {}]) {
        const loaded = load("/example/");
        const operation = loaded.MiniApi.write(value);
        await rejects(operation.shared());
        assert.equal(loaded.requests.length, 0);
    }
});

test("write rejects invalid Section names without fetching", async () => {
    const invalidNames = ["", " leading", "trailing ", "control\u0001", "x".repeat(129)];
    for (const name of invalidNames) {
        const loaded = load("/example/");
        await rejects(loaded.MiniApi.write({[name]: true}).private());
        assert.equal(loaded.requests.length, 0);
    }
});

test("write rejects serialization failures and silently omitted Sections", async () => {
    const circular = {};
    circular.self = circular;
    const cases = [
        {value: circular},
        {value: BigInt(1)},
        {value: undefined},
        {value: function () {}},
        {value: Symbol("value")}
    ];
    for (const data of cases) {
        const loaded = load("/example/");
        await rejects(loaded.MiniApi.write(data).shared());
        assert.equal(loaded.requests.length, 0);
    }

    const transformed = {first: 1, second: 2};
    Object.defineProperty(transformed, "toJSON", {
        enumerable: false,
        value() {
            return {first: 1};
        }
    });
    const loaded = load("/example/");
    await rejects(loaded.MiniApi.write(transformed).shared());
    assert.equal(loaded.requests.length, 0);
});

test("remove uses encoded DELETE routes and resolves undefined without parsing", async () => {
    const textCalls = {count: 0};
    const loaded = load("/example/", () =>
        Promise.resolve(response(204, "ignored", {textCalls})));
    const section = "folder/name & more";
    const result = await loaded.MiniApi.remove(section).private();
    assert.equal(result, undefined);
    assert.equal(
            loaded.requests[0].url,
            "/example/api/private/remove?section=" + encodeURIComponent(section));
    assert.equal(loaded.requests[0].options.method, "DELETE");
    assert.equal(textCalls.count, 0);
});

test("clear uses DELETE and resolves undefined without parsing", async () => {
    const textCalls = {count: 0};
    const loaded = load("/notes/", () =>
        Promise.resolve(response(204, "ignored", {textCalls})));
    const result = await loaded.MiniApi.clear().shared();
    assert.equal(result, undefined);
    assert.equal(loaded.requests[0].url, "/notes/api/shared/clear");
    assert.equal(loaded.requests[0].options.method, "DELETE");
    assert.equal(textCalls.count, 0);
});

test("Section validation is Unicode-aware and accepts approved names", async () => {
    const validNames = [
        "x",
        "x".repeat(128),
        "😀".repeat(128),
        "café",
        "internal space",
        "hyphen-under_score.period",
        "folder/name"
    ];
    for (const name of validNames) {
        const loaded = load("/example/", () => Promise.resolve(response(200, "true")));
        await loaded.MiniApi.read(name).shared();
        assert.equal(loaded.requests.length, 1);
    }

    for (const name of ["😀".repeat(129), "\u00a0leading", "trailing\u00a0", "control\u0085"]) {
        const loaded = load("/example/");
        await rejects(loaded.MiniApi.remove(name).shared());
        assert.equal(loaded.requests.length, 0);
    }
});

test("preserves server status, code, and message for HTTP errors", async () => {
    const errors = [
        [400, "BAD_REQUEST", "Invalid API request."],
        [404, "SECTION_NOT_FOUND", "Section not found."],
        [415, "UNSUPPORTED_MEDIA_TYPE", "Content type must be application/json."],
        [500, "PERSISTENCE_ERROR", "Write failed"]
    ];
    for (const [status, code, message] of errors) {
        const body = JSON.stringify({error: {code, message}});
        const loaded = load("/example/", () => Promise.resolve(response(status, body)));
        await rejects(loaded.MiniApi.readAll().shared(), error => {
            assert.equal(error.status, status);
            assert.equal(error.code, code);
            assert.equal(error.message, message);
        });
    }
});

test("malformed server error JSON still rejects with the HTTP status", async () => {
    const loaded = load("/example/", () => Promise.resolve(response(500, "not json")));
    await rejects(loaded.MiniApi.clear().private(), error => {
        assert.equal(error.status, 500);
        assert.equal(error.code, undefined);
        assert.match(error.message, /500/);
    });
});

test("network failures and unavailable fetch reject", async () => {
    const networkError = new Error("network unavailable");
    const loaded = load("/example/", () => Promise.reject(networkError));
    await rejects(loaded.MiniApi.readAll().shared(), error => {
        assert.equal(error, networkError);
    });

    const unavailable = load("/example/");
    unavailable.window.fetch = undefined;
    await rejects(unavailable.MiniApi.clear().shared());
});

test("unexpected successful statuses reject instead of using response.ok", async () => {
    for (const status of [201, 202, 204]) {
        const loaded = load("/example/", () => Promise.resolve(response(status, "{}")));
        await rejects(loaded.MiniApi.readAll().shared(), error => {
            assert.equal(error.status, status);
        });
    }
    for (const status of [200, 201, 202]) {
        const loaded = load("/example/", () => Promise.resolve(response(status, "{}")));
        await rejects(loaded.MiniApi.clear().shared(), error => {
            assert.equal(error.status, status);
        });
    }
});

(async function run() {
    let passed = 0;
    for (const current of tests) {
        try {
            await current.body();
            passed += 1;
            process.stdout.write("PASS " + current.name + "\n");
        } catch (error) {
            process.stderr.write("FAIL " + current.name + "\n");
            throw error;
        }
    }
    process.stdout.write("\n" + passed + " MiniApi tests passed.\n");
}()).catch(error => {
    console.error(error);
    process.exitCode = 1;
});
