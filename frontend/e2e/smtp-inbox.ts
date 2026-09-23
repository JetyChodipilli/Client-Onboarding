import { createServer } from "node:net";

// Test-only SMTP inbox. Exercises the production SMTP adapter without external delivery.
export async function smtpInbox() {
  const messages: string[] = [];
  const server = createServer((socket) => {
    let buffer = "";
    let data: string[] | undefined;
    socket.write("220 test inbox ready\r\n");
    socket.on("data", (chunk) => {
      buffer += chunk.toString();
      let end: number;
      while ((end = buffer.indexOf("\r\n")) >= 0) {
        const line = buffer.slice(0, end); buffer = buffer.slice(end + 2);
        if (data) {
          if (line === ".") { messages.push(data.join("\n")); data = undefined; socket.write("250 accepted\r\n"); }
          else data.push(line.startsWith("..") ? line.slice(1) : line);
        } else if (line === "DATA") { data = []; socket.write("354 send message\r\n"); }
        else if (line === "QUIT") socket.end("221 goodbye\r\n");
        else if (/^(EHLO|HELO|MAIL FROM:|RCPT TO:)/.test(line)) socket.write("250 OK\r\n");
        else socket.write("500 unsupported test command\r\n");
      }
    });
  });
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(1025, "127.0.0.1", resolve);
  });
  return {
    messages,
    close: () => new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve())),
  };
}
