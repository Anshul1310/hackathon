const express = require('express');
const router = express.Router();
const multer = require('multer');
const File = require('../models/File');
const authMiddleware = require('../middleware/auth');
const { uploadToS3 } = require('../config/s3');

const upload = multer({ storage: multer.memoryStorage() });
const MAX_STORAGE_BYTES = 250 * 1024 * 1024;

router.use(authMiddleware);

router.post('/upload', upload.single('file'), async (req, res) => {
  try {
    console.log('[FILE UPLOAD] Starting upload request for user:', req.user.id);
    if (!req.file) {
      return res.status(400).json({ success: false, message: 'No file provided' });
    }

    const existingFiles = await File.find({ ownerId: req.user.id, isTrashed: false });
    const currentStorageUsed = existingFiles.reduce((acc, f) => acc + (f.size || 0), 0);

    if (currentStorageUsed + req.file.size > MAX_STORAGE_BYTES) {
      console.log('[FILE UPLOAD QUOTA EXCEEDED] Current:', currentStorageUsed, 'File:', req.file.size);
      return res.status(400).json({
        success: false,
        message: 'Storage quota exceeded (250 MB max storage limit)'
      });
    }

    const { parentFolder } = req.body;
    const fileUrl = await uploadToS3(req.file);

    const file = await File.create({
      name: req.file.originalname,
      size: req.file.size,
      mimeType: req.file.mimetype,
      parentFolder: (parentFolder && parentFolder !== 'null' && parentFolder !== 'root') ? parentFolder : null,
      ownerId: req.user.id,
      path: fileUrl
    });

    console.log('[FILE UPLOAD SUCCESS] File created in MongoDB:', file);
    res.status(201).json({ success: true, file });
  } catch (error) {
    console.error('[FILE UPLOAD EXCEPTION]', error);
    res.status(500).json({ success: false, message: error.message });
  }
});

router.post('/', async (req, res) => {
  try {
    const { name, size, mimeType, parentFolder } = req.body;
    if (!name) {
      return res.status(400).json({ success: false, message: 'File name is required' });
    }

    const file = await File.create({
      name,
      size: size || 1024,
      mimeType: mimeType || 'text/plain',
      parentFolder: (parentFolder && parentFolder !== 'null' && parentFolder !== 'root') ? parentFolder : null,
      ownerId: req.user.id
    });

    res.status(201).json({ success: true, file });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.get('/', async (req, res) => {
  try {
    const { parentFolder } = req.query;
    const query = {
      ownerId: req.user.id,
      isTrashed: false,
      parentFolder: (parentFolder && parentFolder !== 'null' && parentFolder !== 'root') ? parentFolder : null
    };

    const files = await File.find(query).sort({ name: 1 });
    res.json({ success: true, files });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.patch('/:id/star', async (req, res) => {
  try {
    const file = await File.findOne({ _id: req.params.id, ownerId: req.user.id });
    if (!file) {
      return res.status(404).json({ success: false, message: 'File not found' });
    }

    file.isFavorite = !file.isFavorite;
    await file.save();

    console.log('[FILE STAR TOGGLED]', file._id, 'isFavorite:', file.isFavorite);
    res.json({ success: true, file });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.get('/trashed', async (req, res) => {
  try {
    const files = await File.find({ ownerId: req.user.id, isTrashed: true }).sort({ createdAt: -1 });
    res.json({ success: true, files });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.get('/starred', async (req, res) => {
  try {
    const files = await File.find({ ownerId: req.user.id, isFavorite: true, isTrashed: false }).sort({ name: 1 });
    res.json({ success: true, files });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

module.exports = router;
