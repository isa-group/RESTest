const axios = require('axios').default
const fs = require('fs')

axios.defaults.baseURL = 'https://www.googleapis.com/youtube'
axios.defaults.headers.common['Authorization'] = 'Bearer XXX'

const timer = 35
const limit = 10

for (let i=0; i<limit; i++) {
    setTimeout(
        function() {
            axios.get(`/v3/comments?part=id&maxResults=100&parentId=UgxjoQSDRyHwcWSWY414AaABAg`)
            .then(function (response) {
                const ids = response.data.items.map(item => item.id)
                console.log(ids)


                let j = 0
                ids.forEach(id => {
                    setTimeout(
                        function() {
                            axios.delete(`/v3/comments?id=${id}`)
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
        (timer-10)*i*100*2
    )
}
