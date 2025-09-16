# AO Compressor

A GUI tool for compressing and decompressing Argentum Online resources into `.ao` files.

`.ao` files are standard ZIP files that contain game resources (images, sounds, etc.) while preserving folder structure
and providing compression.

![](screenshot.png)

## Features

- **Compression**: Converts resource folders into compressed `.ao` files
- **Decompression**: Extracts `.ao` file contents to folders
- **User-friendly interface**: Intuitive GUI with colorized logging and progress bar
- **Asynchronous processing**: Operations run in the background without blocking the interface
- **Security**: Protection against zip bombs and path traversal attacks

## Requirements

- **Java**: 17 or higher
- **Maven**: For compilation and dependency management

## Usage

### Main Interface

The application presents a window with two main buttons:

1. **Compress**: Compress a folder into an `.ao` file
2. **Decompress**: Extract an `.ao` file to a folder

### Compression

1. Click **Compress**
2. Select the folder containing the resources to compress
3. Specify the location and name of the output `.ao` file
4. The application will process all files in the folder recursively

### Decompression

1. Click **Decompress**
2. Select the `.ao` file to extract
3. Select the destination folder where files will be extracted
4. Files will be extracted to a subfolder named `<filename>-descompressed` while maintaining the original folder
   structure

## Project Structure

```
src/main/java/org/aocompressor/
├── App.java           # Main class with GUI and compression/decompression logic
└── Utils.java         # General utilities (file size formatting, SHA-256, links)
```

## Technical Details

- **File Format**: `.ao` files are standard ZIP archives with UTF-8 encoding
- **Compression**: Uses Java's built-in ZIP compression
- **Security**: Implements path traversal protection and directory validation
- **Threading**: Background operations using SwingWorker to keep UI responsive
- **Error Handling**: Comprehensive error handling with user-friendly messages

---

**Note**: `.ao` files are compatible with standard ZIP tools, but using this application is recommended to maintain full
compatibility with Argentum Online.