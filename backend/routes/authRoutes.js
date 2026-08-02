const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const axios = require('axios');
const User = require('../models/User');

const JWT_SECRET = process.env.JWT_SECRET || 'dcloud_secret_jwt_key_2026';

router.post('/github', async (req, res) => {
  try {
    console.log('[AUTH] Received GitHub authentication request:', req.body);
    let { code, username, email, githubId, avatar } = req.body;

    if (code && process.env.GITHUB_CLIENT_ID && process.env.GITHUB_CLIENT_SECRET) {
      const tokenResponse = await axios.post(
        'https://github.com/login/oauth/access_token',
        {
          client_id: process.env.GITHUB_CLIENT_ID,
          client_secret: process.env.GITHUB_CLIENT_SECRET,
          code
        },
        { headers: { Accept: 'application/json' } }
      );

      const accessToken = tokenResponse.data.access_token;
      if (accessToken) {
        const userResponse = await axios.get('https://api.github.com/user', {
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        const ghData = userResponse.data;
        username = ghData.login || username;
        email = ghData.email || email;
        githubId = String(ghData.id);
        avatar = ghData.avatar_url || avatar;
      }
    }

    if (!username && !email) {
      return res.status(400).json({ success: false, message: 'GitHub username or email is required' });
    }

    const targetEmail = email || `${username.toLowerCase()}@github.com`;
    const targetName = username || targetEmail.split('@')[0];

    let user = await User.findOne({ email: targetEmail });

    if (!user) {
      user = await User.create({
        name: targetName,
        email: targetEmail,
        githubId: githubId || `gh_${Date.now()}`,
        avatar: avatar || `https://github.com/${username || 'octocat'}.png`
      });
      console.log('[AUTH SUCCESS] Created GitHub user in MongoDB:', user);
    } else {
      if (githubId) user.githubId = githubId;
      if (avatar) user.avatar = avatar;
      if (username) user.name = username;
      await user.save();
      console.log('[AUTH SUCCESS] Updated GitHub user in MongoDB:', user);
    }

    const token = jwt.sign(
      { id: user._id, email: user.email },
      JWT_SECRET,
      { expiresIn: '7d' }
    );

    res.json({
      success: true,
      token,
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
        avatar: user.avatar
      }
    });
  } catch (error) {
    console.error('[AUTH EXCEPTION]', error);
    res.status(500).json({ success: false, message: error.message });
  }
});

module.exports = router;
