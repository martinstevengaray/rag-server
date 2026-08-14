// The chat client, in the one place it exists. Any page -- this server's own index.html or a
// third-party site -- gets the UI by loading this script and providing a mount point:
//
//   <div data-rag-chat></div>
//   <script src="https://<this-server>/widget.js"></script>
//
// Optional attributes on the mount element: data-rag-placeholder, data-rag-height.
//
// Cross-origin hosts require the function URL's CORS config to permit them (see
// terraform/main.tf); same-origin hosts do not care either way.
(function () {
    // currentScript is only readable while the script is executing, so the origin is resolved
    // now rather than inside a later callback where it would be null.
    const script = document.currentScript;
    const ENDPOINT = script ? new URL(script.src).origin + "/" : "/";

    // Part of the widget rather than the page around it, so every embedding shows the same
    // heading and provenance without copying this markup.
    const TITLE = "Retrieval-Augmented Generation Chat";
    const CORPUS = "Oregon Revised Statutes";
    const STACK = "Java · AWS Lambda · S3 Vectors · OpenAI API";
    const SOURCE_URL = "https://github.com/martinstevengaray/rag-server";

    // Colors are read as custom properties so a host page can theme the widget from any
    // ancestor -- `.my-page { --rag-accent: #7aa2f7 }`. The second argument to each var() is
    // the standalone default: this app's original light palette, deliberately fixed rather
    // than theme-aware, so serving it from here looks the same as it always has.
    const STYLES = `
/* The defaults below are the plain light palette this app has always used, and they are
   deliberately not theme-aware: the widget should look the same served from here no matter
   what the visitor's OS is set to. A host page that wants it to follow its own theme sets
   the --rag-* properties, and its values win at every use site. */
.rag-chat {
    display: flex;
    flex-direction: column;
    font-family: inherit;
    text-align: left;
    color: var(--rag-text, inherit);
}
/* flex:none so the heading keeps its height and only the conversation absorbs the slack */
.rag-header {
    flex: none;
    margin: 0 0 12px;
    text-align: center;
}
.rag-title {
    margin: 2px 0 0;
    font-size: 18px;
    color: var(--rag-text, #444444);
}
.rag-meta {
    margin: 6px 0 0;
    font-size: 12px;
    color: var(--rag-muted, #666666);
}
/* tighter gap between the two small lines so they read as one group under the title */
.rag-meta + .rag-meta {
    margin-top: 2px;
}
.rag-meta a {
    color: var(--rag-accent, #0b5cad);
}
/* The link is appended after the stack text, so the gap in front of it is spacing and belongs
   here. Trailing whitespace in the STACK constant cannot do this job: HTML collapses a run of
   spaces to one, the width is then whatever the font says rather than a value we chose, and
   the gap vanishes the moment someone trims the string. */
.rag-source-link {
    margin-left: 12px;
}
.rag-conversation {
    /* min-height:0 lets this shrink inside a flex column instead of pushing the composer
       off the bottom; without it a flex item refuses to go below its content height. */
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    border: 1px solid var(--rag-border, #dddddd);
    border-radius: 6px;
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    background: var(--rag-surface, #fafafa);
}
.rag-msg {
    max-width: 80%;
    padding: 8px 12px;
    border-radius: 12px;
    white-space: pre-wrap;
    word-wrap: break-word;
    font-size: 14px;
    line-height: 1.4;
}
.rag-msg.rag-user {
    align-self: flex-end;
    background: var(--rag-user, #d6eaff);
    border-bottom-right-radius: 2px;
}
.rag-msg.rag-assistant {
    align-self: flex-start;
    background: var(--rag-assistant, #eeeeee);
    border-bottom-left-radius: 2px;
}
.rag-sources {
    max-width: 80%;
    font-size: 12px;
    color: var(--rag-muted, #555555);
}
.rag-sources.rag-assistant { align-self: flex-start; }
.rag-sources.rag-user { align-self: flex-end; }
.rag-sources-title { font-weight: 600; margin-bottom: 2px; }
.rag-sources ul { margin: 0; padding-left: 18px; }
.rag-sources a {
    color: var(--rag-accent, #0b5cad);
    word-break: break-all;
}
.rag-composer {
    flex: none;
    display: flex;
    flex-direction: column;
    margin-top: 12px;
}
/* The two form controls fall back to the CSS system colors, so with no host theming they
   render as the plain native input and button this app started with. */
.rag-input {
    width: 100%;
    min-height: 72px;
    box-sizing: border-box;
    padding: 8px;
    border: 1px solid var(--rag-border, ButtonBorder);
    border-radius: var(--rag-radius, 2px);
    background: var(--rag-surface, Field);
    color: var(--rag-text, FieldText);
    font-family: inherit;
    font-size: 16px;   /* under 16px iOS Safari zooms in on focus and does not zoom back out */
    resize: vertical;
}
/* Both buttons share one row so the composer keeps its single-column shape. Send stays at
   the right edge under the textarea; Clear Context is pushed to the left edge, far enough
   from Send that the destructive action is not next to the one being clicked repeatedly. */
.rag-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 8px;
}
.rag-send {
    padding: 8px 20px;
    border: 1px solid var(--rag-accent, ButtonBorder);
    border-radius: var(--rag-radius, 4px);
    background: var(--rag-accent, ButtonFace);
    color: var(--rag-on-accent, ButtonText);
    font-family: inherit;
    font-size: 14px;
    cursor: pointer;
}
/* Secondary to Send: the accent shows only in the border and label, so discarding the
   conversation never looks like the button you reach for by default. */
.rag-clear {
    margin-right: auto;   /* absorbs the row's free space, holding Clear Context at the left */
    padding: 8px 20px;
    border: 1px solid var(--rag-border, ButtonBorder);
    border-radius: var(--rag-radius, 4px);
    background: transparent;
    color: var(--rag-muted, ButtonText);
    font-family: inherit;
    font-size: 14px;
    cursor: pointer;
}
.rag-send:disabled,
.rag-clear:disabled { opacity: 0.6; cursor: default; }
.rag-status {
    flex: none;
    margin-top: 8px;
    white-space: pre-wrap;
    color: var(--rag-muted, #444444);
}
.rag-status:empty { margin-top: 0; }
`;

    // Injected ahead of everything already in <head> so a host page's own rules win on equal
    // specificity rather than losing to a stylesheet that happened to be added later.
    function injectStyles() {
        if (document.getElementById("rag-chat-styles")) {
            return;
        }
        const style = document.createElement("style");
        style.id = "rag-chat-styles";
        style.textContent = STYLES;
        document.head.insertBefore(style, document.head.firstChild);
    }

    function mount(host) {
        if (host.dataset.ragMounted === "true") {
            return;   // guard against the script being included twice
        }
        host.dataset.ragMounted = "true";
        host.classList.add("rag-chat");

        if (host.dataset.ragHeight) {
            host.style.height = host.dataset.ragHeight;
        }

        const header = document.createElement("div");
        header.className = "rag-header";

        const title = document.createElement("p");
        title.className = "rag-title";
        title.textContent = TITLE;

        const corpusLine = document.createElement("p");
        corpusLine.className = "rag-meta";
        corpusLine.textContent = "Currently serving: " + CORPUS;

        const stackLine = document.createElement("p");
        stackLine.className = "rag-meta";
        stackLine.textContent = STACK;

        const sourceLink = document.createElement("a");
        sourceLink.className = "rag-source-link";
        sourceLink.href = SOURCE_URL;
        sourceLink.target = "_blank";
        sourceLink.rel = "noopener noreferrer";
        sourceLink.textContent = "Source on GitHub";
        stackLine.appendChild(sourceLink);

        header.appendChild(title);
        header.appendChild(corpusLine);
        header.appendChild(stackLine);

        const conversation = document.createElement("div");
        conversation.className = "rag-conversation";

        const composer = document.createElement("div");
        composer.className = "rag-composer";

        const input = document.createElement("textarea");
        input.className = "rag-input";
        input.placeholder = host.dataset.ragPlaceholder || "Enter text here...";

        const actions = document.createElement("div");
        actions.className = "rag-actions";

        const clearButton = document.createElement("button");
        clearButton.className = "rag-clear";
        clearButton.type = "button";
        clearButton.textContent = "Clear Context";

        const button = document.createElement("button");
        button.className = "rag-send";
        button.type = "button";
        button.textContent = "Send";

        const status = document.createElement("div");
        status.className = "rag-status";
        status.setAttribute("role", "status");

        actions.appendChild(clearButton);
        actions.appendChild(button);
        composer.appendChild(input);
        composer.appendChild(actions);
        host.appendChild(header);
        host.appendChild(conversation);
        host.appendChild(composer);
        host.appendChild(status);

        let inFlight = false;      // guard against overlapping requests
        let sessionState = null;   // null on first request, then echoed back from the server

        // Append a chat bubble and keep the newest message in view.
        // `sources` is an optional array of strings shown beneath the bubble.
        function addMessage(role, text, sources) {
            const msg = document.createElement("div");
            msg.className = "rag-msg rag-" + role;   // role is "user" or "assistant"
            msg.textContent = text;
            conversation.appendChild(msg);

            if (Array.isArray(sources) && sources.length > 0) {
                const box = document.createElement("div");
                box.className = "rag-sources rag-" + role;
                const title = document.createElement("div");
                title.className = "rag-sources-title";
                title.textContent = "Sources";
                box.appendChild(title);

                const list = document.createElement("ul");
                for (const src of sources) {
                    const li = document.createElement("li");
                    if (/^https?:\/\//i.test(src)) {
                        const a = document.createElement("a");
                        a.href = src;
                        a.textContent = src;
                        a.target = "_blank";
                        a.rel = "noopener noreferrer";
                        li.appendChild(a);
                    } else {
                        li.textContent = src;
                    }
                    list.appendChild(li);
                }
                box.appendChild(list);
                conversation.appendChild(box);
            }

            conversation.scrollTop = conversation.scrollHeight;
        }

        // Start over: drop the transcript and the session state together, so the next request
        // goes out as a first request rather than continuing a conversation the user can no
        // longer see.
        function clear() {
            if (inFlight) return;
            conversation.replaceChildren();
            sessionState = null;
            status.textContent = "";
            input.focus();
        }

        async function send() {
            if (inFlight) return;
            const userPrompt = input.value.trim();
            if (userPrompt === "") return;

            inFlight = true;
            button.disabled = true;
            clearButton.disabled = true;
            button.textContent = "Sending…";
            status.textContent = "";

            // Show the user's message immediately and clear the input.
            addMessage("user", userPrompt);
            input.value = "";

            try {
                const response = await fetch(ENDPOINT, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ userPrompt, sessionState })
                });
                if (!response.ok) {
                    throw new Error("HTTP " + response.status);
                }
                const data = await response.json();   // { chatResponse, sources, sessionState, details }
                addMessage("assistant", data.chatResponse, data.sources);
                sessionState = data.sessionState;     // echo this back on the next request
            } catch (err) {
                status.textContent = "Error: " + err;
            } finally {
                inFlight = false;
                button.disabled = false;
                clearButton.disabled = false;
                button.textContent = "Send";
                input.focus();
            }
        }

        button.addEventListener("click", send);
        clearButton.addEventListener("click", clear);

        // Enter sends; Shift+Enter inserts a newline.
        input.addEventListener("keydown", (e) => {
            if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                send();
            }
        });
    }

    function init() {
        injectStyles();
        for (const host of document.querySelectorAll("[data-rag-chat]")) {
            mount(host);
        }
    }

    // The mount point may not be parsed yet if the host put this script in <head>.
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
