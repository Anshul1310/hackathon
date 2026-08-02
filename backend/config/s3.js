const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');
const fs = require('fs');
const path = require('path');

const uploadToS3 = async (file) => {
  const accessKeyId = process.env.AWS_ACCESS_KEY_ID;
  const secretAccessKey = process.env.AWS_SECRET_ACCESS_KEY;
  const region = process.env.AWS_REGION || 'us-east-1';
  const bucketName = process.env.AWS_BUCKET_NAME || 'dcloud-storage-bucket';

  const fileKey = `${Date.now()}_${file.originalname}`;

  console.log(`[S3 Upload Attempt] File: ${file.originalname}, Size: ${file.size} bytes, Mime: ${file.mimetype}`);

  if (accessKeyId && secretAccessKey) {
    console.log(`[S3 Target] Uploading to AWS S3 bucket "${bucketName}" in region "${region}"...`);
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
    const s3Url = `https://${bucketName}.s3.${region}.amazonaws.com/${fileKey}`;
    console.log(`[S3 Success] File uploaded to S3: ${s3Url}`);
    return s3Url;
  } else {
    console.log(`[Local Fallback] AWS credentials missing. Saving file to local uploads directory...`);
    const uploadsDir = path.join(__dirname, '..', 'uploads');
    if (!fs.existsSync(uploadsDir)) {
      fs.mkdirSync(uploadsDir, { recursive: true });
    }
    const localPath = path.join(uploadsDir, fileKey);
    fs.writeFileSync(localPath, file.buffer);
    const localUrl = `/uploads/${fileKey}`;
    console.log(`[Local Success] File saved locally at: ${localUrl}`);
    return localUrl;
  }
};

module.exports = { uploadToS3 };
