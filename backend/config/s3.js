const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');
const fs = require('fs');
const path = require('path');

const uploadToS3 = async (file) => {
  const accessKeyId = process.env.AWS_ACCESS_KEY_ID;
  const secretAccessKey = process.env.AWS_SECRET_ACCESS_KEY;
  const region = process.env.AWS_REGION || 'us-east-1';
  const bucketName = process.env.AWS_BUCKET_NAME || 'dcloud-storage-bucket';

  const fileKey = `${Date.now()}_${file.originalname}`;

  if (accessKeyId && secretAccessKey) {
    const s3Client = new S3Client({
      region,
      credentials: {
        accessKeyId,
        secretAccessKey
      }
    });

    const command = new PutObjectCommand({
      Bucket: bucketName,
      Key: fileKey,
      Body: file.buffer,
      ContentType: file.mimetype
    });

    await s3Client.send(command);
    return `https://${bucketName}.s3.${region}.amazonaws.com/${fileKey}`;
  } else {
    const uploadsDir = path.join(__dirname, '..', 'uploads');
    if (!fs.existsSync(uploadsDir)) {
      fs.mkdirSync(uploadsDir, { recursive: true });
    }
    const localPath = path.join(uploadsDir, fileKey);
    fs.writeFileSync(localPath, file.buffer);
    return `/uploads/${fileKey}`;
  }
};

module.exports = { uploadToS3 };
