const { initializeApp, getApps } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');

const app = initializeApp({ projectId: 'irgadgetsofficialweb' }, 'test');
const auth = getAuth(app);
auth.verifyIdToken('dummy').then(() => console.log('verified')).catch(e => console.log('err:', e.code, e.message));
