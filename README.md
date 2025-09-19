# AO Compressor

A GUI tool for compressing and decompressing Argentum Online resources into `.ao` files.

`.ao` files are standard ZIP files that contain game resources (images, sounds, etc.) while preserving folder structure
and providing efficient compression.

![](screenshot.png)

## Features

- **Compression**: Converts resource folders into compressed `.ao` files
- **Decompression**: Extracts `.ao` file contents to folders
- **Intuitive GUI**: Clean interface with colorized logging and real-time progress tracking
- **Asynchronous processing**: Operations run in the background without blocking the interface
- **Security**: Protection against zip bombs and path traversal attacks
- **Cross-Platform**: Runs on Windows, macOS and Linux

## Requirements

- **Java**: 17 or higher
- **Maven**: For compilation and dependency management

## How to Use

### Main Interface

The application window provides two primary operations:

1. **Compress**: Compress a folder structure into a compressed .ao file
2. **Decompress**: Extract an `.ao` file back to its original folder structure

### Compression

1. Click the **Compress** button
2. Select the source folder containing the resources to compress
3. Choose the destination location and filename for the output `.ao` file
4. The application will process all files in the folder recursively

### Decompression

1. Click the **Decompress** buttom
2. Select the `.ao` file to extract
3. Choose the destination folder for extraction
4. Files will be extracted to a subfolder named `<filename>-decompressed` while maintaining the original directory
   structure

## Project Structure

```
src/main/java/org/aocompressor/
├── App.java                    # Main application class with GUI logic
├── Compressor.java             # Core compression/decompression engine
├── TaskRunner.java             # Builder pattern for background task execution with progress tracking
├── Logger.java                 # Colorized logging system
└── Utils.java                  # Utility functions (file operations, formatting, etc.)
```

## Technical Details

`.ao` files are standard ZIP archives that use Java's built-in ZIP compression algorithm. While they're compatible with
standard ZIP tools, this application is recommended for full Argentum Online compatibility. The tool uses SwingWorker to
perform file operations in background threads, streams file data to minimize memory usage during
compression/decompression, and provides real-time progress indication for long-running operations.