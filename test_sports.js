const axios = require('axios');

async function testFetchPlayer() {
  const url = "https://tutvlive.xyz/mpd/tvfhd.php?sTL1?id=115";
  console.log("Fetching player endpoint:", url);
  try {
    const res = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://tvcstreams.pl/'
      }
    });
    console.log("Status:", res.status);
    console.log("Content-Type:", res.headers['content-type']);
    console.log("Body snippet:", res.data.slice(0, 1000));
  } catch (err) {
    console.error("Error with Referer tvcstreams.pl:", err.message);
  }

  try {
    const res = await axios.get(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://www.verfutbol.at/'
      }
    });
    console.log("\nStatus with Referer verfutbol.at:", res.status);
    console.log("Body snippet:", res.data.slice(0, 1000));
  } catch (err) {
    console.error("Error with Referer verfutbol.at:", err.message);
  }
}

testFetchPlayer();
