const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const User = require('../models/User');

const JWT_SECRET = process.env.JWT_SECRET || 'dcloud_secret_jwt_key_2026';

const parseJwtPayload = (token) => {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = Buffer.from(base64, 'base64').toString('utf8');
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
};

router.post('/google', async (req, res) => {
  try {
    console.log('[AUTH] Received Google authentication request:', req.body);
    let { email, name, googleId, avatar, idToken } = req.body;

    if (idToken) {
      const decoded = parseJwtPayload(idToken);
      if (decoded) {
        if (!email && decoded.email) email = decoded.email;
        if (!name && decoded.name) name = decoded.name;
        if (!googleId && decoded.sub) googleId = decoded.sub;
        if (!avatar && decoded.picture) avatar = decoded.picture;
      }
    }

    if (!email) {
      console.log('[AUTH ERROR] Missing email in auth request');
      return res.status(400).json({ success: false, message: 'Email is required' });
    }

    let user = await User.findOne({ email });

    if (!user) {
      user = await User.create({
        name: name || email.split('@')[0],
        email,
        googleId,
        avatar
      });
      console.log('[AUTH SUCCESS] Created new user in MongoDB:', user);
    } else {
      if (googleId) user.googleId = googleId;
      if (avatar) user.avatar = avatar;
      if (name) user.name = name;
      await user.save();
      console.log('[AUTH SUCCESS] Updated user profile in MongoDB:', user);
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
