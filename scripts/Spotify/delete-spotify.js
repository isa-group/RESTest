const axios = require('axios').default
const fs = require('fs')

axios.defaults.baseURL = 'https://api.spotify.com'
axios.defaults.headers.common['Authorization'] = 'Bearer XXX'

const timer = 90
const limit = 100

for (let i=0; i<limit; i++) {
    setTimeout(
        function() {
            axios.get(`/v1/me/playlists?limit=50`)
            .then(function (response) {
                const ids = response.data.items.map(item => item.id)
                console.log(ids)


                let j = 0
                ids.forEach(id => {
                    setTimeout(
                        function() {
                            axios.delete(`/v1/playlists/${id}/followers`)
                            .then(() => console.log(`Deleted id ${id}`))
                            .catch((error) => console.log(JSON.stringify(error.response.data)))
                        },
                        timer*j
                    )
                    j++;
                });
            })
            .catch((error) => console.log(JSON.stringify(error.response.data)))
        },
        (timer-50)*i*100*2
    )
}
