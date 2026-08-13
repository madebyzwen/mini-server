(function (window, document) {
    "use strict";

    var MiniApi = window.MiniApi;
    var scopeInputs = document.querySelectorAll('input[name="scope"]');
    var scopeBadge = document.getElementById("selected-scope-badge");
    var scopeDescription = document.getElementById("selected-scope-description");
    var outputPanel = document.querySelector(".output-panel");
    var outputState = document.getElementById("output-state");
    var outputSummary = document.getElementById("output-summary");
    var outputValue = document.getElementById("output-value");
    var helloValue = document.getElementById("hello-value");
    var helloStatus = document.getElementById("hello-status");

    var demoData = {
        demo: {
            message: "Written through MiniApi",
            enabled: true
        },
        demoItems: [
            "Alpha",
            "Beta",
            "Gamma"
        ]
    };

    function selectedScope() {
        var selected = document.querySelector('input[name="scope"]:checked');
        return selected ? selected.value : "shared";
    }

    function scopeLabel(scope) {
        return scope === "private" ? "Private" : "Shared";
    }

    function executeForScope(operation, scope) {
        if (scope === "private") {
            return operation.private();
        }
        return operation.shared();
    }

    function formatValue(value) {
        var formatted = JSON.stringify(value, null, 2);
        return formatted === undefined ? String(value) : formatted;
    }

    function errorDetails(error) {
        var lines = [];
        var message = error && error.message ? error.message : "The operation failed.";
        lines.push(message);
        if (error && error.status !== undefined) {
            lines.push("HTTP status: " + error.status);
        }
        if (error && error.code !== undefined) {
            lines.push("Error code: " + error.code);
        }
        return lines.join("\n");
    }

    function setOutput(kind, state, summary, value) {
        outputPanel.setAttribute("data-result", kind);
        outputState.textContent = state;
        outputSummary.textContent = summary;
        outputValue.textContent = value;
    }

    function showPending(action, scope) {
        setOutput(
                "pending",
                "Working",
                action + " · " + scopeLabel(scope) + " scope",
                "Waiting for Mini Server…");
    }

    function showValue(action, scope, value) {
        setOutput(
                "success",
                "Success",
                action + " · " + scopeLabel(scope) + " scope",
                formatValue(value));
    }

    function showSuccess(action, scope, message) {
        setOutput(
                "success",
                "Success",
                action + " · " + scopeLabel(scope) + " scope",
                message);
    }

    function showError(action, scope, error) {
        setOutput(
                "error",
                "Error",
                action + " · " + scopeLabel(scope) + " scope",
                errorDetails(error));
    }

    function runOperation(button, action, operation, onSuccess) {
        var scope = selectedScope();
        button.disabled = true;
        showPending(action, scope);

        executeForScope(operation, scope).then(function (value) {
            onSuccess(scope, value);
        }, function (error) {
            showError(action, scope, error);
        }).then(function () {
            button.disabled = false;
        });
    }

    function updateScopeDisplay() {
        var scope = selectedScope();
        var label = scopeLabel(scope);
        scopeBadge.textContent = label + " selected";
        scopeBadge.className = "scope-badge scope-badge-" + scope;
        scopeDescription.textContent = scope === "private"
                ? "Private data is stored in the current Windows user profile."
                : "Shared data is stored with this application installation.";
    }

    function loadStarterValue() {
        if (!MiniApi) {
            var missingLibrary = new Error("MiniApi could not be loaded.");
            helloValue.textContent = "Starter data unavailable";
            helloStatus.textContent = missingLibrary.message;
            showError("Load starter Section", "shared", missingLibrary);
            return;
        }

        MiniApi.read("start").shared().then(function (value) {
            helloValue.textContent = typeof value === "string" ? value : formatValue(value);
            helloStatus.textContent = "Loaded from shared persistence";
        }, function (error) {
            helloValue.textContent = "Starter data unavailable";
            helloStatus.textContent = errorDetails(error).replace(/\n/g, " · ");
            showError("Load starter Section", "shared", error);
        });
    }

    Array.prototype.forEach.call(scopeInputs, function (input) {
        input.addEventListener("change", updateScopeDisplay);
    });

    document.getElementById("read-button").addEventListener("click", function () {
        var button = this;
        var section = document.getElementById("read-section").value;
        runOperation(button, "Read Section “" + section + "”", MiniApi.read(section), function (scope, value) {
            showValue("Read Section “" + section + "”", scope, value);
        });
    });

    document.getElementById("read-all-button").addEventListener("click", function () {
        var button = this;
        runOperation(button, "Read All", MiniApi.readAll(), function (scope, value) {
            showValue("Read All", scope, value);
        });
    });

    document.getElementById("write-button").addEventListener("click", function () {
        var button = this;
        runOperation(button, "Write Demo Data", MiniApi.write(demoData), function (scope) {
            showSuccess(
                    "Write Demo Data",
                    scope,
                    "Wrote Sections “demo” and “demoItems”. Use Read All to inspect them.");
        });
    });

    document.getElementById("remove-button").addEventListener("click", function () {
        var button = this;
        var section = document.getElementById("remove-section").value;
        runOperation(
                button,
                "Remove Section “" + section + "”",
                MiniApi.remove(section),
                function (scope) {
                    showSuccess(
                            "Remove Section “" + section + "”",
                            scope,
                            "Removed Section “" + section + "”.");
                });
    });

    document.getElementById("clear-button").addEventListener("click", function () {
        var button = this;
        var scope = selectedScope();
        if (!window.confirm("Clear all " + scope + " persistence data for this example?")) {
            setOutput(
                    "neutral",
                    "Cancelled",
                    "Clear · " + scopeLabel(scope) + " scope",
                    "No persistence data was changed.");
            return;
        }
        runOperation(button, "Clear", MiniApi.clear(), function (completedScope) {
            showSuccess(
                    "Clear",
                    completedScope,
                    "Cleared the " + completedScope + " persistence scope.");
        });
    });

    updateScopeDisplay();
    loadStarterValue();
}(window, document));
