const axios = require('axios');
const { validatePlayableUrl } = require('../sourceValidator');

const PLUTO_REGION_IP = '189.201.128.1';
const PLUTO_CLIENT_ID = 'vision-plus-pluto-client-' + Math.random().toString(36).substring(7);
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';

let cachedBootData = null;
let lastBootTime = 0;

async function getBootData() {
  const now = Date.now();
  if (cachedBootData && (now - lastBootTime < 4 * 60 * 60 * 1000)) { // 4 hours cache
    return cachedBootData;
  }

  const d = new Date();
  const clientTime = encodeURI(d.toISOString());
  const headers = {
    'X-Forwarded-For': PLUTO_REGION_IP,
    'User-Agent': USER_AGENT
  };
  
  const bootUrl = `https://boot.pluto.tv/v4/start?appName=web&appVersion=7.9.0-a9cca6b89aea4dc0998b92a51989d2adb9a9025d&deviceVersion=16.2.0&deviceModel=web&deviceMake=Chrome&deviceType=web&clientID=${PLUTO_CLIENT_ID}&clientModelNumber=1.0.0&channelID=5a4d3a00ad95e4718ae8d8db&serverSideAds=true&constraints=&drmCapabilities=&blockingMode=&clientTime=${clientTime}`;
  
  const resp = await axios.get(bootUrl, { headers, timeout: 8000 });
  cachedBootData = resp.data;
  lastBootTime = now;
  return cachedBootData;
}

module.exports = {
  name: 'plutoAdapter',
  
  /**
   * Checks if this adapter can handle the given stream source.
   */
  canHandle(source) {
    return source && source.url && source.url.startsWith('pluto-tv://');
  },
  
  /**
   * Validates and resolves the direct stream URL using Pluto's boot data.
   */
  async resolve(source, options = {}) {
    console.log(`[plutoAdapter] ⚡ Resolviendo stream dinamico de Pluto TV: ${source.url}`);
    
    try {
      const bootData = await getBootData();
      
      let path = source.url.replace(/^pluto-tv:\/\//, '');
      if (!path.startsWith('/')) path = '/' + path;
      
      const serverUrl = bootData.servers.stitcher;
      const stitcherParams = bootData.stitcherParams;
      const jwt = bootData.sessionToken;
      
      const finalUrl = `${serverUrl}/v2${path}?${stitcherParams}&jwt=${jwt}&masterJWTPassthrough=true`;
      
      const result = await validatePlayableUrl(finalUrl);
      
      if (result.success) {
        return {
          success: true,
          stream: {
            url: result.url,
            type: result.type,
            headers: {
              'User-Agent': USER_AGENT
            },
            quality: 'auto'
          }
        };
      }
      
      return {
        success: false,
        reason: result.reason || 'PLUTO_STREAM_UNAVAILABLE'
      };
      
    } catch (error) {
      console.error(`[plutoAdapter] ❌ Error resolviendo Pluto TV: ${error.message}`);
      return {
        success: false,
        reason: 'PLUTO_BOOT_ERROR'
      };
    }
  }
};
