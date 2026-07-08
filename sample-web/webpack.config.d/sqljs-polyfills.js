const webpack = require("webpack");

// Webpack 5 polyfills required by sql.js (peer of @cashapp/sqldelight-sqljs-worker).
config.resolve = {
    ...(config.resolve || {}),
    fallback: {
        ...(config.resolve?.fallback || {}),
        fs: false,
        vm: false,
        path: require.resolve("path-browserify"),
        crypto: require.resolve("crypto-browserify"),
        stream: require.resolve("stream-browserify"),
        buffer: require.resolve("buffer/"),
    },
};

config.plugins = [
    ...(config.plugins || []),
    new webpack.ProvidePlugin({
        Buffer: ["buffer", "Buffer"],
    }),
];