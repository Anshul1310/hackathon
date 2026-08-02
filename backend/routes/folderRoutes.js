const express = require('express');
const router = express.Router();
const Folder = require('../models/Folder');
const File = require('../models/File');
const authMiddleware = require('../middleware/auth');
const { deleteFromS3, getStreamFromS3 } = require('../config/s3');
const { ZipArchive } = require('archiver');

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

router.get('/:id/download', async (req, res) => {
  try {
    const folder = await Folder.findOne({ _id: req.params.id, ownerId: req.user.id });
    if (!folder) {
      return res.status(404).json({ success: false, message: 'Folder not found' });
    }

    // Recursively collect all files under this folder
    async function collectFiles(folderId, pathPrefix) {
      const results = [];
      const files = await File.find({ parentFolder: folderId, ownerId: req.user.id, isTrashed: false });
      for (const file of files) {
        results.push({ file, archivePath: `${pathPrefix}/${file.name}` });
      }
      const subFolders = await Folder.find({ parentFolder: folderId, ownerId: req.user.id, isTrashed: false });
      for (const sub of subFolders) {
        const subResults = await collectFiles(sub._id, `${pathPrefix}/${sub.name}`);
        results.push(...subResults);
      }
      return results;
    }

    const allFiles = await collectFiles(folder._id, folder.name);

    res.setHeader('Content-Disposition', `attachment; filename="${encodeURIComponent(folder.name)}.zip"`);
    res.setHeader('Content-Type', 'application/zip');

    const archive = new ZipArchive({ zlib: { level: 5 } });

    archive.on('error', (err) => {
      console.error('[Archive Error]', err);
      if (!res.headersSent) {
        res.status(500).json({ success: false, message: 'Archive error' });
      }
    });

    archive.pipe(res);

    for (const entry of allFiles) {
      try {
        const stream = await getStreamFromS3(entry.file.path);
        if (stream) {
          archive.append(stream, { name: entry.archivePath });
        }
      } catch (e) {
        console.error(`[Archive Skip] Could not add file ${entry.file.name}:`, e.message);
      }
    }

    // If folder is empty, add an empty directory entry so the ZIP isn't empty
    if (allFiles.length === 0) {
      archive.append('', { name: `${folder.name}/.keep` });
    }

    await archive.finalize();
  } catch (error) {
    console.error('[Folder Download Error]', error);
    if (!res.headersSent) {
      res.status(500).json({ success: false, message: error.message });
    }
  }
});

module.exports = router;
