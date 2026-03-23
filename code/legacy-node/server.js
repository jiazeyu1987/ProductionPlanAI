const { createApp } = require("./app");

const PORT = Number(process.env.PORT || 3001);
const { app } = createApp();

app.listen(PORT, () => {
  // eslint-disable-next-line no-console
  console.log(`[mvp] server listening on :${PORT}`);
});
