(function (window) {
    "use strict";

    var JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    var MAX_SECTION_LENGTH = 128;
    var CONTROL_CHARACTERS = /[\u0000-\u001f\u007f-\u009f]/;

    function operation(execute) {
        return Object.freeze({
            private: function () {
                return run(execute, "private");
            },
            shared: function () {
                return run(execute, "shared");
            }
        });
    }

    function run(execute, scope) {
        return Promise.resolve().then(function () {
            return execute(scope);
        });
    }

    function sitePath() {
        var pathname = window.location && window.location.pathname;
        if (typeof pathname !== "string" || pathname.charAt(0) !== "/") {
            throw new Error("Unable to determine the application site.");
        }

        var rawSite = pathname.split("/")[1];
        var site;
        try {
            site = decodeURIComponent(rawSite);
        } catch (error) {
            throw new Error("Unable to determine the application site.");
        }

        if (!site
                || site === "."
                || site === ".."
                || site.toLowerCase() === "_shared"
                || /[\\/:]/.test(site)
                || CONTROL_CHARACTERS.test(site)) {
            throw new Error("Unable to determine the application site.");
        }
        return "/" + encodeURIComponent(site);
    }

    function endpoint(scope, name) {
        return sitePath() + "/api/" + scope + "/" + name;
    }

    function validateSection(section) {
        if (typeof section !== "string") {
            throw new Error("Section name is invalid.");
        }

        var length = Array.from(section).length;
        if (length < 1
                || length > MAX_SECTION_LENGTH
                || section !== section.trim()
                || CONTROL_CHARACTERS.test(section)) {
            throw new Error("Section name is invalid.");
        }
    }

    function encodedSection(section) {
        validateSection(section);
        return encodeURIComponent(section);
    }

    function fetchRequest(url, options, expectedStatus, parseJson) {
        if (typeof window.fetch !== "function") {
            throw new Error("Fetch is unavailable.");
        }

        return window.fetch(url, options).then(function (response) {
            if (!response || response.status !== expectedStatus) {
                return serverError(response).then(function (error) {
                    throw error;
                });
            }
            if (!parseJson) {
                return undefined;
            }
            if (typeof response.text !== "function") {
                throw new Error("The server returned an invalid JSON response.");
            }
            return response.text().then(function (body) {
                if (!body) {
                    throw new Error("The server returned an invalid JSON response.");
                }
                try {
                    return JSON.parse(body);
                } catch (error) {
                    throw new Error("The server returned an invalid JSON response.");
                }
            });
        });
    }

    function serverError(response) {
        var status = response && typeof response.status === "number"
                ? response.status
                : undefined;
        var fallback = apiError(status, null, status === undefined
                ? "Request failed."
                : "Request failed with HTTP status " + status + ".");

        if (!response || typeof response.text !== "function") {
            return Promise.resolve(fallback);
        }
        return response.text().then(function (body) {
            try {
                var parsed = JSON.parse(body);
                var details = parsed && parsed.error;
                if (details
                        && typeof details.code === "string"
                        && typeof details.message === "string") {
                    return apiError(status, details.code, details.message);
                }
            } catch (error) {
                // The HTTP status remains the useful error information.
            }
            return fallback;
        }, function () {
            return fallback;
        });
    }

    function apiError(status, code, message) {
        var error = new Error(message);
        if (status !== undefined) {
            error.status = status;
        }
        if (code !== null) {
            error.code = code;
        }
        return error;
    }

    function serializeWrite(data) {
        if (data === null || typeof data !== "object" || Array.isArray(data)) {
            throw new Error("Write data must be a non-empty object.");
        }

        var requestedSections = Object.keys(data);
        if (requestedSections.length === 0) {
            throw new Error("Write data must be a non-empty object.");
        }
        requestedSections.forEach(validateSection);

        var body = JSON.stringify(data);
        if (typeof body !== "string") {
            throw new Error("Write data could not be serialized.");
        }

        var serialized;
        try {
            serialized = JSON.parse(body);
        } catch (error) {
            throw new Error("Write data could not be serialized.");
        }
        if (serialized === null || typeof serialized !== "object" || Array.isArray(serialized)) {
            throw new Error("Write data could not be serialized.");
        }

        var serializedSections = Object.keys(serialized);
        if (serializedSections.length !== requestedSections.length
                || requestedSections.some(function (section) {
                    return !Object.prototype.hasOwnProperty.call(serialized, section);
                })) {
            throw new Error("Write data could not be serialized without losing Sections.");
        }
        return body;
    }

    var MiniApi = Object.freeze({
        read: function (section) {
            return operation(function (scope) {
                var url = endpoint(scope, "read")
                        + "?section=" + encodedSection(section);
                return fetchRequest(url, {method: "GET"}, 200, true);
            });
        },

        readAll: function () {
            return operation(function (scope) {
                return fetchRequest(
                        endpoint(scope, "readAll"),
                        {method: "GET"},
                        200,
                        true);
            });
        },

        write: function (data) {
            return operation(function (scope) {
                var body = serializeWrite(data);
                return fetchRequest(
                        endpoint(scope, "write"),
                        {
                            method: "POST",
                            headers: {"Content-Type": JSON_CONTENT_TYPE},
                            body: body
                        },
                        204,
                        false);
            });
        },

        remove: function (section) {
            return operation(function (scope) {
                var url = endpoint(scope, "remove")
                        + "?section=" + encodedSection(section);
                return fetchRequest(url, {method: "DELETE"}, 204, false);
            });
        },

        clear: function () {
            return operation(function (scope) {
                return fetchRequest(
                        endpoint(scope, "clear"),
                        {method: "DELETE"},
                        204,
                        false);
            });
        }
    });

    window.MiniApi = MiniApi;
}(window));
