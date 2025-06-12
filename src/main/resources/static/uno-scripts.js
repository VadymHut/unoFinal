
let stompClient = null;
let wsConnected = false;
let gameId = null;
let jwt = null;
let latestState = null;
let lastElo = null;
let pollingInterval = null;

document.addEventListener("DOMContentLoaded", () => {
    setupThemeToggle();
    stylizeRankedToggle();
    applySavedTheme();
    initializeApp();
});


function stylizeRankedToggle() {
    const style = document.createElement("style");
    style.innerHTML = `
        .ranked-toggle {
            position: relative;
            display: inline-block;
            width: 60px;
            height: 34px;
        }
        .ranked-toggle input {
            opacity: 0;
            width: 0;
            height: 0;
        }
        .slider {
            position: absolute;
            cursor: pointer;
            top: 0; left: 0; right: 0; bottom: 0;
            background-color: #ccc;
            transition: .4s;
            border-radius: 34px;
        }
        .slider:before {
            position: absolute;
            content: "";
            height: 26px;
            width: 26px;
            left: 4px;
            bottom: 4px;
            background-color: white;
            transition: .4s;
            border-radius: 50%;
        }
        input:checked + .slider {
            background-color: #f00;
        }
        input:checked + .slider:before {
            transform: translateX(26px);
        }
        body.dark {
            background-color: #1e1e1e;
            color: white;
        }
        body.light {
            background-color: #fff;
            color: black;
        }
        .glow {
        box-shadow: 0 0 15px 5px rgba(0, 200, 255, 0.7);
        transition: box-shadow 0.3s ease-in-out;
        border-radius: 8px;
    }
    `;
    document.head.appendChild(style);

    const rawCheckbox = document.querySelector("#rankedToggle");
    if (rawCheckbox) {
        const wrapper = document.createElement("label");
        wrapper.classList.add("ranked-toggle");

        const customSlider = document.createElement("span");
        customSlider.classList.add("slider");

        rawCheckbox.parentElement.insertBefore(wrapper, rawCheckbox);
        wrapper.appendChild(rawCheckbox);
        wrapper.appendChild(customSlider);
    }
}


function decodeJwtPayload(token) {
    try {
        const payload = token.split('.')[1];
        return JSON.parse(atob(payload));
    } catch (e) {
        return {};
    }
}

function log(msg) {
    const time = new Date().toLocaleTimeString();
    const panel = document.getElementById("logPanel");
    if (panel) {
        panel.textContent += `[${time}] ${msg}\n`;
        panel.scrollTop = panel.scrollHeight;
    }
    const info = document.getElementById("info");
    if (info) info.innerText = msg;
    console.log("[INFO]", msg);
}

function setGameUIVisible(visible) {
    console.log(`🔧 setGameUIVisible(${visible}) called`);

    const elements = ["topCard", "hand", "statusBar"];
    elements.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.style.display = visible ? 'block' : 'none';
            console.log(`Element #${id} display set to:`, el.style.display);
        } else {
            console.warn(`Element with id="${id}" not found in DOM`);
        }
    });
}


function initializeApp() {
    console.log("initializeApp() called");
    setGameUIVisible(false);

    const storedJwt = sessionStorage.getItem("jwt");
    const jwtInput = document.getElementById("jwt");
    if (storedJwt && jwtInput) {
        jwt = storedJwt;
        jwtInput.value = jwt;
        console.log("JWT restored from sessionStorage:", jwt);
    } else {
        console.warn("No JWT found in sessionStorage or #jwt input missing");
    }

    const codeInput = document.getElementById("code");
    const code = codeInput ? codeInput.value.trim() : null;

    console.log("Retrieved game code:", code);

    if (code && jwt && jwt.startsWith("eyJ")) {
        log(`Attempting state fetch for code=${code}`);
        console.log("Fetching game state with JWT and code");

        fetch(`/api/games/state/${code}`, {
            headers: { Authorization: "Bearer " + jwt }
        })
            .then(res => {
                console.log("State fetch response status:", res.status);
                if (!res.ok) throw new Error("Failed to fetch game state");
                return res.json();
            })
            .then(data => {
                gameId = data.gameId;
                console.log("Game state fetched. Game ID:", gameId);
                connectWebSocket();
                showState(data);
                setGameUIVisible(true);
            })
            .catch(err => {
                log("❌ State fetch failed: " + err.message);
                console.error("❌ Fetch error:", err.message);
                sessionStorage.removeItem("jwt");
            });
    } else {
        log("ℹ️ No JWT or game code - ready for new game.");
        console.warn("initializeApp skipped - JWT or code missing or malformed");
    }
}

function connectWebSocket() {
    console.log("🔌 connectWebSocket() called");

    if (wsConnected || !jwt || jwt.length < 10) {
        console.warn("WebSocket not connected - wsConnected:", wsConnected, "jwt valid:", jwt && jwt.length >= 10);
        log("WebSocket already connected or JWT invalid.");
        return;
    }

    const socketUrl = `/ws-sockjs?Authorization=Bearer%20${encodeURIComponent(jwt)}`;
    console.log("Connecting to WebSocket via SockJS:", socketUrl);
    const socket = new SockJS(socketUrl);
    stompClient = Stomp.over(socket);

    const decoded = decodeJwtPayload(jwt);
    const userId = decoded.sub || decoded.id || "[unknown]";
    log(`Connecting as user: ${userId}`);

    stompClient.connect(
        { Authorization: "Bearer " + jwt },
        frame => {
            wsConnected = true;
            log("WebSocket connected as user: " + userId);
            console.log("STOMP connection frame:", frame);

            stompClient.subscribe("/user/queue/game", message => {
                console.log("Message received on /user/queue/game:", message.body);
                try {
                    const state = JSON.parse(message.body);
                    latestState = state;
                    log("Game update received via WebSocket");
                    showState(state);
                } catch (e) {
                    log("❌ Failed to parse game state. Error: " + e.message);
                    console.error("❌ JSON parse error:", e);
                }
            });

            stompClient.subscribe("/user/queue/errors", message => {
                log("❌ STOMP error: " + message.body);
                console.error("❌ Error on /user/queue/errors:", message.body);
            });
        },
        error => {
            console.error("❌ WebSocket connection failed:", error);
            log("❌ WebSocket error. Reconnecting in 3s...");
            setTimeout(() => {
                wsConnected = false;
                connectWebSocket();
            }, 3000);
        }
    );
}




function createGame() {
    console.log("createGame() invoked");

    const jwtInput = document.getElementById("jwt");
    if (!jwtInput) {
        console.error("JWT input element not found");
        alert("JWT input not found");
        return;
    }

    jwt = jwtInput.value.trim();
    console.log("JWT:", jwt);

    if (!jwt) {
        console.warn("No JWT provided");
        alert("Paste your JWT first.");
        return;
    }

    const userInfo = decodeJwtPayload(jwt);
    const userId = userInfo.sub || userInfo.id || "unknown";
    log("Creating game as user: " + userId);
    console.log("Sending game creation request for userId:", userId);

    const rankedToggle = document.getElementById("rankedToggle");
    const ranked = rankedToggle ? rankedToggle.checked : false;
    console.log("Game mode:", ranked ? "Ranked" : "Unranked");

    fetch("/api/games", {
        method: "POST",
        headers: {
            "Authorization": "Bearer " + jwt,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ ranked })
    })
        .then(res => {
            console.log("Received response:", res.status);
            if (!res.ok) {
                return res.text().then(errBody => {
                    throw new Error(`Game creation failed [${res.status}]: ${errBody}`);
                });
            }
            return res.text();
        })
        .then(code => {
            console.log("Game code received:", code);

            const codeInput = document.getElementById("code");
            if (codeInput) {
                codeInput.value = code;
                console.log("Code populated in #code input");
            }

            const createdCodeInput = document.getElementById("createdCode");
            if (createdCodeInput) {
                createdCodeInput.style.display = "block";
                createdCodeInput.value = code;
                console.log("📋 Code shown in #createdCode field");
            }

            log(`Game created. Code: ${code} ${ranked ? "(Ranked)" : "(Unranked)"}`);
            console.log("Waiting for another player to join...");

            startWaitingForJoin(code);
        })
        .catch(err => {
            log("❌ " + err.message);
            console.error("❌ Game creation error:", err.message);
            Swal.fire({
                icon: 'error',
                title: 'Game Creation Failed',
                text: err.message
            });
        });
}




function joinGame() {
    console.log("🔁 joinGame() invoked");

    const jwtInput = document.getElementById("jwt");
    const codeInput = document.getElementById("code");

    if (!jwtInput || !codeInput) {
        console.error("❌ Required input elements not found: jwtInput or codeInput is null");
        alert("Required elements not found");
        return;
    }

    jwt = jwtInput.value.trim();
    const code = codeInput.value.trim();

    console.log("JWT:", jwt);
    console.log("Code:", code);

    if (!jwt || !code) {
        console.warn("JWT or Game Code is missing");
        alert("Please enter both JWT and Game Code.");
        return;
    }

    const userInfo = decodeJwtPayload(jwt);
    const userId = userInfo.sub || userInfo.id || "unknown";
    log("🆔 Joining game as user: " + userId);
    console.log("📬 Sending join request for game:", code, "as user:", userId);

    fetch(`/api/games/${code}/join`, {
        method: "POST",
        headers: {
            "Authorization": "Bearer " + jwt
        }
    })
        .then(res => {
            console.log("Received response from join API:", res.status);
            if (!res.ok) {
                return res.text().then(body => {
                    throw new Error(`Failed to join game: ${res.status} ${body}`);
                });
            }
            return res.json();
        })
        .then(data => {
            gameId = data.gameId;
            log("Joined game successfully. gameId = " + gameId);
            console.log("Redirecting to match.html with code =", code);
            window.location.href = `match.html?code=${code}`;
        })
        .catch(err => {
            console.error("Join game failed:", err.message);
            log("Join error: " + err.message);
            Swal.fire({
                icon: 'error',
                title: 'Join Failed',
                text: err.message
            });
        });
}




function showState(state) {
    console.log("showState() called with:", state);
    if (!state) {
        console.warn("showState was called with null or undefined state");
        return;
    }

    latestState = state;

    if (state.ranked && state.status !== "FINISHED") {
        lastElo = state.yourElo;
        console.log("Ranked match - storing current ELO:", lastElo);
    }

    const isYourTurn = state.currentSeat === state.yourSeat;
    console.log(`Turn info: YourSeat=${state.yourSeat}, CurrentSeat=${state.currentSeat}, IsYourTurn=${isYourTurn}`);
    log(`Seat: You=${state.yourSeat}, Current=${state.currentSeat}, YourTurn=${isYourTurn}`);
    console.log("Full game state object:", state);

    const topCardEl = document.getElementById("topCard");
    if (topCardEl) {
        topCardEl.innerHTML = "";
        if (state.topCard) {
            const img = document.createElement("img");
            img.src = `/images/${formatCardFilename(state.topCard)}.png`;
            img.alt = `${state.topCard.color} ${state.topCard.rank}`;
            topCardEl.appendChild(img);
            console.log("🃏 Top card rendered:", state.topCard);
        } else {
            topCardEl.innerText = `Top Card: (none) | Active Color: ${state.activeColor}`;
            console.warn("No top card found in state");
        }
    } else {
        console.error("Element with id='topCard' not found in DOM");
    }

    const turnImg = document.getElementById("turnIndicator");
    const statusText = document.getElementById("statusText");
    if (turnImg && statusText) {
        turnImg.style.display = "block";
        turnImg.src = isYourTurn ? "/images/YOURTURN.png" : "/images/OPPTURN.png";
        turnImg.alt = isYourTurn ? "Your Turn" : "Opponent's Turn";

        let status = `You: ${state.yourUsername} | Opponent: ${state.opponentUsername}`;
        status += ` | Opponent has ${state.opponentHandSize} card(s)`;

        if (state.ranked) {
            status += ` | Ranked | Elo: You ${state.yourElo}, Opponent ${state.opponentElo}`;
        } else {
            status += ` | Unranked`;
        }

        if (state.status === "FINISHED") {
            status += " 🏁 Game Over!";
            const youWon = state.yourHand.length === 0;
            status += youWon ? " You won!" : " You lost!";

            if (state.ranked && lastElo !== null) {
                const delta = state.yourElo - lastElo;
                const change = delta > 0 ? ` +${delta}` : delta < 0 ? ` ${delta}` : "";
                if (change) status += ` | Elo Change: ${change}`;
            }
        }

        statusText.innerText = status;
        console.log(" Turn indicator + status updated");
    } else {
        console.error(" Turn indicator or statusText element missing in DOM");
    }

    if (state.status === "IN_PROGRESS") {
        console.log(" Game is in progress - showing game UI");
        setGameUIVisible(true);
        showSection("gameSection");
    } else {
        console.warn(" Game is not yet in progress - status =", state.status);
        log(" Waiting for another player to join...");
    }

    const handDiv = document.getElementById("hand");
    if (handDiv) {
        handDiv.innerHTML = "";

        if (!state.yourHand || state.yourHand.length === 0) {
            console.warn(" Your hand is empty");
            handDiv.innerHTML = "<p class='text-warning'>No cards received. Waiting for game start?</p>";
        } else {
            console.log(" Rendering hand with", state.yourHand.length, "cards");
            state.yourHand.forEach(card => {
                const div = document.createElement("div");
                div.className = "card";

                const img = document.createElement("img");
                img.src = `/images/${formatCardFilename(card)}.png`;
                img.alt = `${card.color} ${card.rank}`;
                img.style.width = "80px";
                img.style.height = "120px";
                img.style.display = "block";

                const isPlayable = isYourTurn && state.status !== "FINISHED";
                div.style.opacity = isPlayable ? "1" : "0.4";
                div.style.pointerEvents = isPlayable ? "auto" : "none";

                if (isPlayable) {
                    div.onclick = () => playCard(card);
                }

                div.appendChild(img);
                handDiv.appendChild(div);
                console.log("🃏 Card rendered:", card);
            });
        }
    } else {
        console.error(" Element with id='hand' not found in DOM");
    }

    if (state.status !== "WAITING" && pollingInterval) {
        clearInterval(pollingInterval);
        pollingInterval = null;
        log(" Stopped polling - game is in progress or finished.");
    }
}



function startPollingIfWaiting(code) {
    if (pollingInterval) clearInterval(pollingInterval);

    pollingInterval = setInterval(() => {
        fetch(`/api/games/state/${code}`, {
            headers: { Authorization: "Bearer " + jwt }
        })
            .then(res => {
                if (!res.ok) throw new Error("Polling request failed");
                return res.json();
            })
            .then(data => {
                if (data.status !== "WAITING") {
                    log(" Poll detected status change. Updating state.");
                    showState(data);
                    clearInterval(pollingInterval);
                    pollingInterval = null;
                }
            })
            .catch(err => {
                log(" Polling error: " + err.message);
                clearInterval(pollingInterval);
                pollingInterval = null;
            });
    }, 3000);
}

function playCard(card) {
    if (!stompClient?.connected || latestState?.status === "FINISHED") return;

    if (card.color === "WILD") {
        Swal.fire({
            title: 'Choose a Color',
            input: 'radio',
            inputOptions: {
                RED: '🔴 Red',
                GREEN: '🟢 Green',
                BLUE: '🔵 Blue',
                YELLOW: '🟡 Yellow'
            },
            inputValidator: value => {
                if (!value) return 'You need to choose a color!';
            },
            confirmButtonText: 'Play Card',
            showCancelButton: true,
            cancelButtonText: 'Cancel',
            customClass: {
                popup: 'text-dark'
            }
        }).then(result => {
            if (result.isConfirmed && result.value) {
                sendCardPlay(card, result.value);
            }
        });
    } else {
        sendCardPlay(card);
    }
}

function sendCardPlay(card, chosenColor = null) {
    stompClient.send(`/app/game.${gameId}.move`, {}, JSON.stringify({
        type: "PLAY_CARD",
        card: card,
        chosenColor: chosenColor
    }));
}


function drawCard() {
    if (!stompClient?.connected || latestState?.status === "FINISHED") return;

    stompClient.send(`/app/game.${gameId}.move`, {}, JSON.stringify({ type: "DRAW_CARD" }));
}

function callUno() {
    if (!stompClient?.connected || latestState?.status === "FINISHED") return;
    stompClient.send(`/app/game.${gameId}.move`, {}, JSON.stringify({ type: "CALL_UNO" }));
}


function login() {
    const usernameInput = document.getElementById("username");
    const passwordInput = document.getElementById("password");

    if (!usernameInput || !passwordInput) {
        Swal.fire({
            icon: 'error',
            title: 'Login Error',
            text: 'Login form not found.'
        });
        return;
    }

    const username = usernameInput.value.trim();
    const password = passwordInput.value.trim();

    if (!username || !password) {
        Swal.fire({
            icon: 'warning',
            title: 'Missing Fields',
            text: 'Username and password are required.'
        });
        return;
    }

    fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
    })
        .then(res => {
            if (!res.ok) throw new Error("Login failed with status " + res.status);
            return res.json();
        })
        .then(data => {
            const jwt = data.token;
            if (jwt && jwt.startsWith("eyJ")) {
                sessionStorage.setItem("jwt", jwt);

                Swal.fire({
                    icon: 'success',
                    title: 'Welcome!',
                    text: 'Login successful! Redirecting...',
                    timer: 2000,
                    showConfirmButton: false
                });

                setTimeout(() => {
                    window.location.href = "index.html";
                }, 2000);
            } else {
                Swal.fire({
                    icon: 'error',
                    title: 'Invalid Token',
                    text: 'Login failed. Invalid token received.'
                });
            }
        })
        .catch(err => {
            Swal.fire({
                icon: 'error',
                title: 'Login Error',
                text: err.message
            });
        });
}


function viewProfile() {
    const storedJwt = sessionStorage.getItem("jwt");
    if (!storedJwt) {
        Swal.fire({
            icon: 'warning',
            title: 'Not Logged In',
            text: 'Please login first to view your profile.'
        });
        return;
    }
    window.location.href = "profile.html";
}

function viewHistory() {
    const storedJwt = sessionStorage.getItem("jwt");
    if (!storedJwt) {
        Swal.fire({
            icon: 'warning',
            title: 'Not Logged In',
            text: 'Please login first to view game history.'
        });
        return;
    }
    window.location.href = "history.html";
}


function formatCardFilename(card) {
    const color = (card.color || "wild").toLowerCase();
    const rank = card.rank.toLowerCase().replace(" ", "_");
    return `${color}_${rank}`;
}


function validatePassword(password) {
    const minLength = 8;
    const hasUpper = /[A-Z]/.test(password);
    const hasLower = /[a-z]/.test(password);

    if (password.length < minLength) {
        Swal.fire({
            icon: 'warning',
            title: 'Weak Password',
            text: 'Password must be at least 8 characters long.'
        });
        return false;
    }

    if (!hasUpper) {
        Swal.fire({
            icon: 'warning',
            title: 'Weak Password',
            text: 'Password must contain at least one uppercase letter.'
        });
        return false;
    }

    if (!hasLower) {
        Swal.fire({
            icon: 'warning',
            title: 'Weak Password',
            text: 'Password must contain at least one lowercase letter.'
        });
        return false;
    }

    return true;
}

function validateAndRegister() {
    const username = document.getElementById("username").value.trim();
    const password = document.getElementById("password").value.trim();

    if (!validatePassword(password)) return;

    fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
    })
        .then(res => {
            if (!res.ok) throw new Error("Registration failed.");
            return fetch("/api/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, password })
            });
        })
        .then(res => {
            if (!res.ok) throw new Error("Auto-login failed after registration.");
            return res.json();
        })
        .then(data => {
            if (data.token?.startsWith("eyJ")) {
                sessionStorage.setItem("jwt", data.token);

                Swal.fire({
                    icon: 'success',
                    title: 'Success!',
                    text: 'Registration successful! Redirecting...',
                    timer: 2000,
                    showConfirmButton: false
                });

                setTimeout(() => {
                    window.location.href = "index.html";
                }, 2000);
            } else {
                Swal.fire({
                    icon: 'error',
                    title: 'Unexpected Token',
                    text: data.token
                });
            }
        })
        .catch(err => {
            Swal.fire({
                icon: 'error',
                title: 'Registration Error',
                text: err.message
            });
        });
}

function startWaitingForJoin(code) {
    const interval = setInterval(() => {
        fetch(`/api/games/state/${code}`, {
            headers: { Authorization: "Bearer " + jwt }
        })
            .then(res => res.json())
            .then(data => {
                if (data.status !== "WAITING") {
                    clearInterval(interval);
                    console.log("👥 Player joined — starting game");
                    window.location.href = `match.html?code=${code}`;
                } else {
                    console.log("⌛ Still waiting for player...");
                }
            })
            .catch(err => {
                console.warn("Polling error:", err.message);
            });
    }, 1000);
}

document.addEventListener("DOMContentLoaded", () => {
    console.log("🚀 initializeApp() launched");
    initializeApp();
});

