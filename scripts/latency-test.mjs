// scripts/latency-test.mjs — run with: node scripts/latency-test.mjs
import { Client } from "@stomp/stompjs";
import WebSocket from "ws";

const WS_URL = process.env.WS_URL ?? "ws://localhost:8081/ws";     // Instance B — subscriber
const API_URL = process.env.API_URL ?? "http://localhost:8080";    // Instance A — hold creator
let SHOW_ID = process.env.SHOW_ID;
let TOKEN_B = process.env.TOKEN_B;
let TOKEN_A = process.env.TOKEN_A;
let USER_ID = process.env.USER_ID ? Number(process.env.USER_ID) : null;
const TRIALS = Number(process.env.TRIALS ?? 20);

function extractUserId(token) {
  try {
    const payload = JSON.parse(Buffer.from(token.split(".")[1], "base64").toString());
    return payload.userId;
  } catch {
    return 1;
  }
}

// Helper to authenticate or register a test user if tokens are not passed via env
async function getAuthToken(name, email, password) {
  try {
    const loginRes = await fetch(`${API_URL}/api/v1/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (loginRes.ok) {
      const data = await loginRes.json();
      return { token: data.accessToken, userId: extractUserId(data.accessToken) };
    }
  } catch (err) {
    // ignore and try register
  }

  const regRes = await fetch(`${API_URL}/api/v1/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, email, password }),
  });
  if (regRes.ok) {
    const data = await regRes.json();
    // Now login to get token
    const loginRes = await fetch(`${API_URL}/api/v1/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (loginRes.ok) {
      const loginData = await loginRes.json();
      return { token: loginData.accessToken, userId: extractUserId(loginData.accessToken) };
    }
    return { token: null, userId: data.id };
  }
  throw new Error(`Authentication failed for ${email}`);
}

function connect(token) {
  return new Promise((resolve, reject) => {
    const client = new Client({
      webSocketFactory: () => new WebSocket(WS_URL),
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => resolve(client),
      onStompError: (f) => reject(new Error(f.headers.message)),
      onWebSocketError: (e) => reject(e),
    });
    client.activate();
  });
}

async function holdSeat(showId, seatId, userId, token) {
  const res = await fetch(`${API_URL}/api/v1/shows/${showId}/holds`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ userId, seatIds: [seatId] }),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`hold failed: ${res.status} ${text}`);
  }
}

async function ensureSeedData() {
  if (!TOKEN_A || !USER_ID) {
    const authA = await getAuthToken("Tester A", "testerA@ticketrush.com", "Password123!");
    TOKEN_A = TOKEN_A ?? authA.token;
    USER_ID = USER_ID ?? authA.userId ?? extractUserId(TOKEN_A);
  }
  if (!TOKEN_B) {
    const authB = await getAuthToken("Tester B", "testerB@ticketrush.com", "Password123!");
    TOKEN_B = TOKEN_B ?? authB.token;
  }

  if (!SHOW_ID) {
    const showsRes = await fetch(`${API_URL}/api/v1/shows?size=1`, {
      headers: { Authorization: `Bearer ${TOKEN_A}` },
    });
    if (showsRes.ok) {
      const showsData = await showsRes.json();
      if (showsData.content && showsData.content.length > 0) {
        SHOW_ID = showsData.content[0].id;
      }
    }
  }
  if (!SHOW_ID) SHOW_ID = "1";
}

async function fetchAvailableSeats(showId, count) {
  const res = await fetch(`${API_URL}/api/v1/shows/${showId}/seatmap`, {
    headers: { Authorization: `Bearer ${TOKEN_A}` },
  });
  if (!res.ok) throw new Error(`Failed to fetch seatmap for show ${showId}`);
  const data = await res.json();
  const seats = data.seats || data;
  const available = seats.filter((s) => s.status === "AVAILABLE").map((s) => s.id);
  if (available.length < count) {
    throw new Error(`Not enough available seats in show ${showId} (needed ${count}, found ${available.length})`);
  }
  return available.slice(0, count);
}

async function main() {
  console.log(`Connecting to WS at ${WS_URL} and API at ${API_URL}...`);
  await ensureSeedData();

  console.log(`Using SHOW_ID=${SHOW_ID}, USER_ID=${USER_ID}`);
  const seatIds = await fetchAvailableSeats(SHOW_ID, TRIALS);

  const subscriber = await connect(TOKEN_B);
  console.log(`Subscriber connected to ${WS_URL}. Subscribing to /topic/shows/${SHOW_ID}/seats...`);
  const latencies = [];

  for (let i = 0; i < TRIALS; i++) {
    const seatId = seatIds[i];
    const received = new Promise((resolve) => {
      const sub = subscriber.subscribe(`/topic/shows/${SHOW_ID}/seats`, (frame) => {
        const update = JSON.parse(frame.body);
        if (update.seatIds && update.seatIds.includes(seatId)) {
          sub.unsubscribe();
          resolve(performance.now());
        }
      });
    });

    const t0 = performance.now(); // single clock — avoids cross-machine clock-skew
    await holdSeat(SHOW_ID, seatId, USER_ID, TOKEN_A);
    const t1 = await received;
    const latency = t1 - t0;
    latencies.push(latency);
    process.stdout.write(`Trial ${i + 1}/${TRIALS}: seat ${seatId} -> ${latency.toFixed(1)}ms\n`);
    await new Promise((r) => setTimeout(r, 200));
  }

  await subscriber.deactivate();

  latencies.sort((a, b) => a - b);
  const p50 = latencies[Math.floor(latencies.length * 0.5)];
  const p95 = latencies[Math.floor(latencies.length * 0.95)];
  console.log(`\n================ Latency Results ================`);
  console.log(`trials=${latencies.length} p50=${p50.toFixed(1)}ms p95=${p95.toFixed(1)}ms max=${latencies.at(-1).toFixed(1)}ms`);
  console.log(`=================================================`);
}

main().catch((e) => {
  console.error("Latency test error:", e);
  process.exit(1);
});
