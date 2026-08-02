const express = require('express');
const router = express.Router();
const Folder = require('../models/Folder');
const File = require('../models/File');
const authMiddleware = require('../middleware/auth');
const { deleteFromS3 } = require('../config/s3');

router.use(authMiddleware);

router.post('/', async (req, res) => {
  try {
    const { name, parentFolder } = req.body;
    if (!name) {
      return res.status(400).json({ success: false, message: 'Folder name is required' });
    }

    const folder = await Folder.create({
      name,
      parentFolder: (parentFolder && parentFolder !== 'null' && parentFolder !== 'root') ? parentFolder : null,
      ownerId: req.user.id
    });

    res.status(201).json({ success: true, folder });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.get('/', async (req, res) => {
  try {
    const { parentFolder, sort } = req.query;
    const query = {
      ownerId: req.user.id,
      isTrashed: false,
      parentFolder: (parentFolder && parentFolder !== 'null' && parentFolder !== 'root') ? parentFolder : null
    };

    let sortOption = { name: 1 };
    if (sort === 'name_desc') sortOption = { name: -1 };
    else if (sort === 'date_asc') sortOption = { createdAt: 1 };
    else if (sort === 'date_desc') sortOption = { createdAt: -1 };

    const folders = await Folder.find(query).sort(sortOption);
    res.json({ success: true, folders });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.patch('/:id/rename', async (req, res) => {
  try {
    const { name } = req.body;
    if (!name) {
      return res.status(400).json({ success: false, message: 'New folder name is required' });
    }

    const folder = await Folder.findOne({ _id: req.params.id, ownerId: req.user.id });
    if (!folder) {
      return res.status(404).json({ success: false, message: 'Folder not found' });
    }

    folder.name = name;
    await folder.save();

    res.json({ success: true, folder });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.patch('/:id/star', async (req, res) => {
  try {
    const folder = await Folder.findOne({ _id: req.params.id, ownerId: req.user.id });
    if (!folder) {
      return res.status(404).json({ success: false, message: 'Folder not found' });
    }

    folder.isFavorite = !folder.isFavorite;
    await folder.save();

    res.json({ success: true, folder });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.patch('/:id/trash', async (req, res) => {
  try {
    const folder = await Folder.findOne({ _id: req.params.id, ownerId: req.user.id });
    if (!folder) {
      return res.status(404).json({ success: false, message: 'Folder not found' });
    }

    folder.isTrashed = true;
    await folder.save();

    res.json({ success: true, folder });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.patch('/:id/restore', async (req, res) => {
  try {
    const folder = await Folder.findOne({ _id: req.params.id, ownerId: req.user.id });
    if (!folder) {
      return res.status(404).json({ success: false, message: 'Folder not found' });
    }

    folder.isTrashed = false;
    await folder.save();

    res.json({ success: true, folder });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.delete('/:id', async (req, res) => {
  try {
    const folder = await Folder.findOne({ _id: req.params.id, ownerId: req.user.id });
    if (!folder) {
      return res.status(404).json({ success: false, message: 'Folder not found' });
    }

    const childFiles = await File.find({ parentFolder: folder._id, ownerId: req.user.id });
    for (const f of childFiles) {
      await deleteFromS3(f.path);
      await File.deleteOne({ _id: f._id });
    }

    await Folder.deleteOne({ _id: folder._id });

    res.json({ success: true, message: 'Folder deleted permanently' });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.get('/trashed', async (req, res) => {
  try {
    const folders = await Folder.find({ ownerId: req.user.id, isTrashed: true }).sort({ createdAt: -1 });
    res.json({ success: true, folders });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

router.get('/starred', async (req, res) => {
  try {
    const folders = await Folder.find({ ownerId: req.user.id, isFavorite: true, isTrashed: false }).sort({ name: 1 });
    res.json({ success: true, folders });
  } catch (error) {
    res.status(500).json({ success: false, message: error.message });
  }
});

module.exports = router;
