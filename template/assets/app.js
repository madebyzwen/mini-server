(function (window, document) {
    "use strict";

    var MiniApi = window.MiniApi;
    var helloTitle = document.getElementById("hello-title");
    var helloValue = document.getElementById("hello-value");
    var sectionName = document.getElementById("section-name");
    var resultSummary = document.getElementById("result-summary");
    var resultValue = document.getElementById("result-value");

    function selectedScope() {
        return document.querySelector('input[name="scope"]:checked').value;
    }

    function executeForScope(operation, scope) {
        if (scope === "private") {
            return operation.private();
        }
        return operation.shared();
    }

    function format(value) {
        var formatted = JSON.stringify(value, null, 2);
        return formatted === undefined ? String(value) : formatted;
    }

    function errorText(error) {
        var lines = [error && error.message ? error.message : "The operation failed."];
        if (error && error.status !== undefined) {
            lines.push("HTTP status: " + error.status);
        }
        if (error && error.code !== undefined) {
            lines.push("Error code: " + error.code);
        }
        return lines.join("\n");
    }

    function run(label, operation, successMessage) {
        var scope = selectedScope();
        resultSummary.textContent = label + " · " + scope;
        resultValue.textContent = "Working…";

        executeForScope(operation, scope).then(function (value) {
            resultSummary.textContent = "Success · " + scope;
            resultValue.textContent = successMessage || format(value);
        }, function (error) {
            resultSummary.textContent = "Error · " + scope;
            resultValue.textContent = errorText(error);
        });
    }

    function loadStarterData() {
        if (!MiniApi) {
            helloTitle.textContent = "MiniApi could not be loaded";
            helloValue.textContent = "Check that /_shared/mini-api.js is available.";
            return;
        }

        MiniApi.read("start").shared().then(function (value) {
            helloTitle.textContent = typeof value === "string" ? value : format(value);
            helloValue.textContent = "Loaded with MiniApi.read(\"start\").shared()";
        }, function (error) {
            helloTitle.textContent = "Starter data could not be loaded";
            helloValue.textContent = errorText(error).replace(/\n/g, " · ");
        });
    }

    document.getElementById("read").addEventListener("click", function () {
        run("Read", MiniApi.read(sectionName.value));
    });

    document.getElementById("read-all").addEventListener("click", function () {
        run("Read All", MiniApi.readAll());
    });

    document.getElementById("write").addEventListener("click", function () {
        run(
                "Write Demo",
                MiniApi.write({
                    demo: {
                        message: "Written through MiniApi",
                        enabled: true
                    }
                }),
                "Wrote the demo Section.");
    });

    document.getElementById("remove").addEventListener("click", function () {
        var section = sectionName.value;
        run("Remove", MiniApi.remove(section), "Removed Section “" + section + "”.");
    });

    document.getElementById("clear").addEventListener("click", function () {
        var scope = selectedScope();
        if (window.confirm("Clear all " + scope + " persistence for this application?")) {
            run("Clear", MiniApi.clear(), "Cleared the " + scope + " scope.");
        }
    });

    loadStarterData();
}(window, document));
