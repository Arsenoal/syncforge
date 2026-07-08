const CopyWebpackPlugin = require("copy-webpack-plugin");

config.plugins = [
    ...(config.plugins || []),
    new CopyWebpackPlugin({
        patterns: [
            {
                from: require.resolve("sql.js/dist/sql-wasm.wasm"),
                to: "sql-wasm.wasm",
            },
        ],
    }),
];